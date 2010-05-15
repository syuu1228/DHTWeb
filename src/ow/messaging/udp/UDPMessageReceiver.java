/*
 * Copyright 2006-2009 National Institute of Advanced Industrial Science
 * and Technology (AIST), and contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ow.messaging.udp;

import java.io.IOException;
import java.io.Serializable;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import ow.id.IDAddressPair;
import ow.messaging.ExtendedMessageHandler;
import ow.messaging.InetMessagingAddress;
import ow.messaging.Message;
import ow.messaging.MessageHandler;
import ow.messaging.MessageReceiver;
import ow.messaging.MessageSender;
import ow.messaging.MessagingAddress;
import ow.messaging.Signature;
import ow.messaging.Tag;
import ow.messaging.upnp.Mapping;
import ow.messaging.util.UPnPAddressPortMapper;
import ow.stat.MessagingReporter;
import ow.stat.StatConfiguration;
import ow.stat.StatFactory;
import ow.util.Timer;
import ow.util.concurrent.SingletonThreadPoolExecutors;
import ow.util.concurrent.ExecutorBlockingMode;

public final class UDPMessageReceiver implements MessageReceiver, Runnable {
	private final static Logger logger = Logger.getLogger("messaging");

	public static InetMessagingAddress selfAddr;
	protected DatagramChannel sock;
	protected UDPMessagingConfiguration config;
	protected UDPMessagingProvider provider;
	private UDPMessageSender sender;
	protected SocketPool sockPool;
	private Thread receiverThread;
	private Set<Thread> handlerThreads = Collections.synchronizedSet(new HashSet<Thread>());

	private List<MessageHandler> handlerList = new ArrayList<MessageHandler>();
	protected boolean extMessageHandlerRegistered = false;

	private final MessagingReporter msgReporter;

	private static boolean oomPrinted = false;

	// for UDP hole punching
	private Thread holePunchingDaemon = null;

	protected UDPMessageReceiver(InetAddress selfInetAddr, int port, int portRange,
			UDPMessagingConfiguration config, UDPMessagingProvider provider) throws IOException {
		this.config = config;
		this.provider = provider;

		// prepare a server socket
		this.sock = DatagramChannel.open();

		// prepare local address
		if (selfInetAddr == null) {
			selfInetAddr = InetAddress.getLocalHost();
		}

		// bind to the specified address, and then to a local address if failed.
		DatagramSocket s = sock.socket();
		//s.setReuseAddress(true);	// for development

		this.selfAddr = this.bind(s, selfInetAddr, port, portRange);

		if (this.selfAddr == null && !selfInetAddr.equals(InetAddress.getLocalHost())) {
			InetMessagingAddress boundAddr = this.bind(s, InetAddress.getLocalHost(), port, portRange);
			if (boundAddr != null) {
				boundAddr.setInetAddress(selfInetAddr);
				this.selfAddr = boundAddr;
			}
		}

		if (this.selfAddr == null) {
			String addrPort = selfInetAddr.getHostAddress() + ":" + port + "-" + (port + portRange - 1);
			logger.log(Level.SEVERE, "Could not bind to " + addrPort + "."
					+ " Specify self hostname with -s option.");
			throw new IOException("Bind failed: " + addrPort);
		}

		this.sockPool = new SocketPool(config.getSocketPoolSize());

		StatConfiguration conf = StatFactory.getDefaultConfiguration();
		this.msgReporter = StatFactory.getMessagingReporter(conf, provider, this.getSender());

		this.sender = (UDPMessageSender)this.getSender(true);

		// for UDP hole punching
		this.doHolePunching = this.config.getDoUDPHolePunching();
		this.punchingRetry = this.config.getPunchingRetry();

		// for UPnP Address Port Mapping
		if (this.config.getDoUPnPNATTraversal()) {
			String internalAddress = this.selfAddr.getHostAddress();
			UPnPAddressPortMapper.start(internalAddress, port, Mapping.Protocol.UDP,
					"Overlay Weaver",
					this.provider, config.getUPnPTimeout());
		}
	}

	private InetMessagingAddress bind(
			DatagramSocket sock, InetAddress inetAddr, int port, int range) {
		InetMessagingAddress addr = null;
		boolean bound = false;

		if (range <= 0) range = 1;
		for (int i = 0; i < range; i++) {
			addr = new InetMessagingAddress(inetAddr, port + i);

			try {
				sock.bind(addr.getInetSocketAddress());

				port = port + i;
				bound = true;
				break;
			}
			catch (IOException e) { /*ignore*/ }
		}

		if (!bound) addr = null;

		return addr;
	}

	public MessagingAddress getSelfAddress() { return this.selfAddr; }
	public void setSelfAddress(String hostOrIP) {
		try {
			this.selfAddr = this.provider.getMessagingAddress(
					hostOrIP, this.selfAddr.getPort());
		}
		catch (UnknownHostException e) {
			logger.log(Level.WARNING, "Could not resolve a hostname: " + hostOrIP);
		}
	}

	public int getPort() { return this.selfAddr.getPort(); }

	public MessagingReporter getMessagingReporter() { return this.msgReporter; }

	public MessageSender getSender() {
		return getSender(false);
	}

	private MessageSender getSender(boolean forReceiver) {
		// does not share a sender
		return new UDPMessageSender(this, forReceiver);
	}

	public void start() {
		synchronized (this) {
			if (receiverThread == null) {
				receiverThread = new Thread(this);
				receiverThread.setDaemon(true);
				receiverThread.setName("UDPMessageReceiver");

				// give higher priority
				receiverThread.setPriority(Thread.currentThread().getPriority()
						+ this.config.getReceiverThreadPriority());

				receiverThread.start();
			}
		}
	}

	public void stop() {
		synchronized (this) {
			if (this.receiverThread != null) {
				this.receiverThread.interrupt();
				this.receiverThread = null;
			}
		}

		Thread[] handlerArray = new Thread[this.handlerThreads.size()];
		this.handlerThreads.toArray(handlerArray);
		for (int i = 0; i < handlerArray.length; i++) {
			handlerArray[i].interrupt();
		}

		this.handlerThreads.clear();

		// for UDP hole punching
		synchronized (this) {
			if (this.holePunchingDaemon != null) {
				this.holePunchingDaemon.interrupt();
				this.holePunchingDaemon = null;
			}
		}

		// notify statistics collector
		this.msgReporter.notifyStatCollectorOfDeletedNode(
				IDAddressPair.getIDAddressPair(null, this.selfAddr), this.selfAddr, -1);
	}

	public void addHandler(MessageHandler handler) {
		List<MessageHandler> newHandlerList = new ArrayList<MessageHandler>();

		synchronized (this) {
			newHandlerList.addAll(this.handlerList);	// copy
			newHandlerList.add(handler);

			this.handlerList = newHandlerList;	// substitute
		}

		if (handler instanceof ExtendedMessageHandler) {
			this.extMessageHandlerRegistered = true;
		}
	}

	public void removeHandler(MessageHandler handler) {
		List<MessageHandler> newHandlerList = new ArrayList<MessageHandler>();

		synchronized (this) {
			newHandlerList.addAll(this.handlerList);	// copy
			newHandlerList.remove(handler);

			this.handlerList = newHandlerList;	// substitute
		}

		boolean exists = false;
		for (MessageHandler h: newHandlerList) {
			if (h instanceof ExtendedMessageHandler) {
				exists = true;
				break;
			}
		}
		this.extMessageHandlerRegistered = exists;
	}

	public void run() {
		ByteBuffer buf = ByteBuffer.allocate(UDPMessageSender.MAX_MSG_SIZE);
		InetSocketAddress srcAddr = null;

		while (true) {
			// receive
			buf.clear();
			try {
				srcAddr = (InetSocketAddress)this.sock.receive(buf);
			}
			catch (IOException e) {
				logger.log(Level.WARNING, "DatagramSocket#receive() threw an Exception and the receiver will die.");
				return;
			}
			buf.rewind();

			logger.log(Level.INFO, "Source address: " + srcAddr);

			// construct a message
			Message msg;
			try {
				msg = Message.decode(buf);
			}
			catch (IOException e) {
				logger.log(Level.WARNING, "Could not decode the received message (corrupted ?).", e);
				continue;
			}

			// check signature
			byte[] acceptableSig = this.provider.getMessageSignature();
			byte[] sig = msg.getSignature();
	/*
	System.out.println("signature:");
	System.out.print(" acceptable:");
	for (int i = 0; i < acceptableSig.length; i++)
	System.out.print(" " + Integer.toHexString(acceptableSig[i] & 0xff));
	System.out.println();
	System.out.print(" message:   ");
	for (int i = 0; i < acceptableSig.length; i++)
	System.out.print(" " + Integer.toHexString(sig[i] & 0xff));
	System.out.println();
	*/
			if (!Signature.match(sig, acceptableSig))
				continue;

			// invoke a Thread handling the incoming Message
			Runnable r = new UDPMessageHandler(srcAddr, msg);

			try {
				if (this.config.getUseThreadPool()) {	// note: does not register to handlerThreads
					SingletonThreadPoolExecutors.getThreadPool(
							ExecutorBlockingMode.NON_BLOCKING, false).submit(r);
				}
				else {
					Thread thr = new Thread(r);
					thr.setDaemon(false);

					handlerThreads.add(thr);

					thr.start();
				}
			}
			catch (OutOfMemoryError e) {
				logger.log(Level.SEVERE, "# of threads: " + Thread.activeCount(), e);

//				synchronized (UDPMessageReceiver.class) {
//					if (!UDPMessageReceiver.oomPrinted) {
//						UDPMessageReceiver.oomPrinted = true;
	//
//						Thread[] tarray = new Thread[Thread.activeCount()];
//						Thread.enumerate(tarray);
//						for (Thread t: tarray) if (t != null) System.out.println("Th: " + t.getName());
//						System.out.flush();
//					}
//				}

				throw e;
			}
		}	// while (true)
	}

	private class UDPMessageHandler implements Runnable {
		private InetSocketAddress srcAddr;
		private Message msg;

		UDPMessageHandler(InetSocketAddress srcAddress, Message message) {
			this.srcAddr = srcAddress;
			this.msg = message;
		}

		public void run() {
			Thread th = Thread.currentThread();
			String origName = th.getName();
			th.setName("UDPMessageHandler: " + this.srcAddr);

			// handling an incoming message
			int tag = msg.getTag();
			Message ret = null;

			if (tag == Tag.PUNCH_HOLE_REQ.getNumber()) {		// for UDP hole punching
				MessagingAddress src = provider.getMessagingAddress(this.srcAddr);

				ret = UDPMessagingMessageFactory.getPunchHoleRepMessage(
						IDAddressPair.getIDAddressPair(null, selfAddr),
						(InetMessagingAddress)src);
			}
			else if (tag == Tag.PUNCH_HOLE_REP.getNumber()) {	// for UDP hole punching
				// notify
				synchronized (punchingLock) {
					punchReplyReceived = true;
					punchingLock.notifyAll();
				}

				if (doHolePunching) {
					Serializable[] contents = msg.getContents();
					InetMessagingAddress selfExteriorAddress = (InetMessagingAddress)contents[0];

					logger.log(Level.INFO,
							"UDP hole punching: self exterior address is " + selfExteriorAddress);

					if (selfExteriorAddress.equals(selfAddr)) {
						// UDP hole punching is not required
						logger.log(Level.INFO, "UDP hole punching was *not* required.");
					}
					else {
						// set self address
						UDPMessageReceiver.this.selfAddr = selfExteriorAddress;

						synchronized (UDPMessageReceiver.this) {
							if (holePunchingDaemon == null) {
								logger.log(Level.INFO, "UDP hole punching is required.");

								// start punching daemon
								Runnable r = new UDPHolePunchingDaemon();
								holePunchingDaemon = new Thread(r);
								holePunchingDaemon.setName("UDPHolePunchingDaemon");
								holePunchingDaemon.setDaemon(true);
								holePunchingDaemon.start();
							}
						}
					}
				}
			}
			else {
				// process the received message
				ret = processMessage(msg);
			}

			// return a Message (from the last handler)
			if (ret != null) {
				logger.log(Level.INFO, "Return a message.");

				MessagingAddress src =
					(msg.getSource() != null ? msg.getSource().getAddress() : null);
				try {
					ByteBuffer buf = sender.send(sock, this.srcAddr, src, ret, true);

					// notify statistics collector
					if (src != null) {
						msgReporter.notifyStatCollectorOfMessageSent(src, ret, buf.remaining());
					}
				}
				catch (IOException e) {
					logger.log(Level.WARNING, "Could not return a message.");

					// notify statistics collector
					if (src != null) {
						msgReporter.notifyStatCollectorOfDeletedNode(
								ret.getSource(), src, ret.getTag());
					}
				}
			}
			else {
				logger.log(Level.INFO, "Return no message.");
			}

			if (tag != Tag.PUNCH_HOLE_REQ.getNumber()
					&& tag != Tag.PUNCH_HOLE_REP.getNumber()) {		// not for UDP hole punching
				// post-process
				postProcessMessage(msg);
			}

			handlerThreads.remove(Thread.currentThread());

			th.setName(origName);
		}
	}

	protected Message processMessage(Message msg) {
		// call every handlers
		List<MessageHandler> currentHandlerList;
		synchronized (this) {
			currentHandlerList = handlerList;
		}

		Message ret = null;

		for (MessageHandler handler: currentHandlerList) {
			try {
				ret = handler.process(msg);
			}
			catch (Throwable e) {
				logger.log(Level.SEVERE, "A MessageHandler#process() threw an Exception.", e);
			}
		}

		return ret;
	}

	protected void postProcessMessage(Message msg) {
		if (!this.extMessageHandlerRegistered) return;

		// call every handlers
		List<MessageHandler> currentHandlerList;
		synchronized (this) {
			currentHandlerList = handlerList;
		}

		for (MessageHandler handler: currentHandlerList) {
			ExtendedMessageHandler extHandler;
			try {
				extHandler = (ExtendedMessageHandler)handler;
			}
			catch (ClassCastException e) { continue; }

			try {
				extHandler.postProcess(msg);
			}
			catch (Throwable e) {
				logger.log(Level.SEVERE, "A MessageHandler#postProcess() threw an Exception.", e);
			}
		}
	}

	//
	// for UDP hole punching
	//

	private final boolean doHolePunching;
	private int punchingRetry;
	private boolean firstPunchingDone = false;
	private boolean firstPunchingOnGoing = false;
	private Object firstPunchingLock = new Object();
	protected InetMessagingAddress lastSendDest = null;
	private long lastSendTime = 0L;

	private Object punchingLock = new Object();
	private volatile boolean punchReplyReceived;

	protected synchronized boolean isFirstPunchingDone() {
		synchronized (this.firstPunchingLock) {
			return this.firstPunchingDone;
		}
	}

	protected boolean beginFirstPunching() {		// returns true if to do punching
		synchronized (this.firstPunchingLock) {
			if (punchingRetry <= 0) {
				return false;
			}
			punchingRetry--;

			while (firstPunchingOnGoing) {
				try {
					this.firstPunchingLock.wait();
				}
				catch (InterruptedException e) {
					// ignore
				}
			}

			if (firstPunchingDone) {
				return false;
			}
			else {
				this.firstPunchingOnGoing = true;
				return true;
			}
		}
	}

	protected void endFirstPunching(boolean success) {
		synchronized (this.firstPunchingLock) {
			this.firstPunchingOnGoing = false;
			this.firstPunchingDone = success;

			this.firstPunchingLock.notifyAll();
		}
	}

	protected void setLastSend(InetMessagingAddress dest) {
		this.lastSendDest = dest;

		if (this.isFirstPunchingDone()) {
			this.lastSendTime = Timer.currentTimeMillis();
		}
	}

	protected synchronized void punchHole() {
		if (!this.doHolePunching)
			return;

		if (this.lastSendDest == null)
			return;

		long curTime = Timer.currentTimeMillis();
		long interval = this.config.getPunchingInterval();

		if (curTime < this.lastSendTime + interval)
			return;

		// ask self exterior address to an other node
		Message reqMsg = UDPMessagingMessageFactory.getPunchHoleReqMessage(
				IDAddressPair.getIDAddressPair(null, this.selfAddr));

		synchronized (this.punchingLock) {
			this.punchReplyReceived = false;

			try {
				this.sender.send(this.lastSendDest, reqMsg);
			}
			catch (IOException e) {
				logger.log(Level.WARNING,
						"Could not send an PUNCH_HOLE_REQ message for UDP hole punching: "
						+ this.lastSendDest + " on " + this.selfAddr, e);
				return;
			}

			// wait for a reply
			if (!this.punchReplyReceived) {
				try {
					this.punchingLock.wait(this.config.getPunchingRepTimeout());
				}
				catch (InterruptedException e) {
					// do nothing
				}
			}

			this.endFirstPunching(this.punchReplyReceived);

			if (!this.punchReplyReceived) {
				logger.log(Level.WARNING,
						"Could not receive an PUNCH_HOLE_REP message for UDP hole punching: "
						+ this.lastSendDest + " on " + this.selfAddr);
			}
		}	// synchronized (this.punchingLock)
	}

	private class UDPHolePunchingDaemon implements Runnable {
		public void run() {
			try {
				while (true) {
					Thread.sleep(UDPMessageReceiver.this.config.getPunchingCheckInterval());

					if (Thread.interrupted())
						break;

					punchHole();
				}
			}
			catch (InterruptedException e) {
				logger.log(Level.WARNING, "UDPHolePunchingDaemon interrupted and die.", e);
			}
		}
	}
}
