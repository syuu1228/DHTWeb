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

package ow.messaging.tcp;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SocketChannel;
import java.util.logging.Level;
import java.util.logging.Logger;

import ow.messaging.InetMessagingAddress;
import ow.messaging.Message;
import ow.messaging.MessageSender;
import ow.messaging.MessagingAddress;
import ow.messaging.Tag;
import ow.stat.MessagingReporter;
import ow.util.AlarmClock;
import ow.util.Timer;
import ow.util.concurrent.SingletonThreadPoolExecutors;
import ow.util.concurrent.ExecutorBlockingMode;

public class TCPMessageSender implements MessageSender {
	private final static Logger logger = Logger.getLogger("messaging");

	private final TCPMessageReceiver receiver;

	protected TCPMessageSender(TCPMessageReceiver receiver) {
		this.receiver = receiver;
	}

	public void send(MessagingAddress dest, Message msg) throws IOException {
		this.adjustLoopbackAddress((InetMessagingAddress)dest);

		// destination is local
		MessagingAddress selfAddress = this.receiver.getSelfAddress();
		if (dest.equals(selfAddress)) {
			this.receiver.processMessage(msg);
			this.receiver.postProcessMessage(msg);
			return;
		}

		// destination is remote
		SocketAddress sockAddr = ((InetMessagingAddress)dest).getInetSocketAddress();
		SocketChannel sock = null;

		int retryCount = 0;
		while (true) {
			try {
				sock = this.receiver.connPool.get(sockAddr);
			}
			catch (IOException e) {
				logger.log(Level.INFO, "Failed to connect: " + dest);

				// notify statistics collector
				MessagingReporter msgReporter = this.receiver.getMessagingReporter();
				if (msgReporter != null) {
					msgReporter.notifyStatCollectorOfDeletedNode(
							msg.getSource(), dest, msg.getTag());
				}

				// rethrow Exception and dispose socket
				throw e;
			}

			try {
				this.send(sock, dest, msg);

				break;
			}
			catch (ClosedChannelException e) {
				// sock is stale. retry once.
				if (retryCount <= 0) {
					retryCount++;
					continue;
				}
				else {
					throw e;
				}
			}
		}

		this.receiver.connPool.put(sockAddr, sock);
	}

	private void send(SocketChannel sock, MessagingAddress dest, Message msg)  throws IOException {
		logger.log(Level.INFO, "send(" + dest + ", " + Tag.getNameByNumber(msg.getTag()) + ")");

		// set signature
		byte[] sig = this.receiver.provider.getMessageSignature();
		msg.setSignature(sig);

		// send
		try {
			ByteBuffer buf = Message.encode(sock, msg);

			// notify statistics collector
			MessagingReporter msgReporter = this.receiver.getMessagingReporter();
			if (msgReporter != null) {
				msgReporter.notifyStatCollectorOfMessageSent(dest, msg, buf.remaining());
			}
		}
		catch (IOException e) {
			logger.log(Level.WARNING, "Could not write a message.");

			// notify statistics collector
			MessagingReporter msgReporter = this.receiver.getMessagingReporter();
			if (msgReporter != null) {
				msgReporter.notifyStatCollectorOfDeletedNode(
						msg.getSource(), dest, msg.getTag());
			}

			throw e;
		}
	}

	public Message sendAndReceive(MessagingAddress dest, final Message msg)
			throws IOException {
		this.adjustLoopbackAddress((InetMessagingAddress)dest);

		Message ret = null;

		// destination is local
		MessagingAddress selfAddress = this.receiver.getSelfAddress();
		if (dest.equals(selfAddress)) {
			ret = this.receiver.processMessage(msg);

			if (this.receiver.extMessageHandlerRegistered) {
				Runnable r = new Runnable () {
					public void run() {
						TCPMessageSender.this.receiver.postProcessMessage(msg);
					}
				};

				if (TCPMessageSender.this.receiver.config.getUseThreadPool()) {
					SingletonThreadPoolExecutors.getThreadPool(
							ExecutorBlockingMode.NON_BLOCKING, false).submit(r);
				}
				else {
					Thread t = new Thread(r);
					t.setName("TCPMessageSender: post-processing thread");
					t.setDaemon(false);
					t.start();
				}
			}

			return ret;
		}

		// destination is remote
		SocketAddress sockAddr = ((InetMessagingAddress)dest).getInetSocketAddress();
		SocketChannel sock = null;

		long timeout, start;

		int retryCount = 0;
		while (true) {
			// prepare socket
			try {
				sock = this.receiver.connPool.get(sockAddr);
			}
			catch (IOException e) {
				logger.log(Level.INFO, "Failed to connect: " + dest);

				// notify statistics collector
				MessagingReporter msgReporter = this.receiver.getMessagingReporter();
				if (msgReporter != null) {
					msgReporter.notifyStatCollectorOfDeletedNode(
							msg.getSource(), dest, msg.getTag());
				}

				// rethrow Exception and dispose socket
				throw e;
			}

			// receive and dispose remaining data
			ByteBuffer buf = ByteBuffer.allocate(1024);
			sock.configureBlocking(false);
			while (true) {
				int read = sock.read(buf);
				if (read <= 0) break;
				buf.clear();

				logger.log(Level.INFO, "Data have remained in a pooled stream: " + read);
			}
			sock.configureBlocking(true);

			// send
			timeout = this.receiver.provider.getTimeoutCalculator().calculateTimeout(dest);
			start = Timer.currentTimeMillis();

			try {
				this.send(sock, dest, msg);

				break;
			}
			catch (ClosedChannelException e) {
				// sock is stale. retry once.
				if (retryCount <= 0) {
					retryCount++;
					continue;
				}
				else {
					throw e;
				}
			}
		}

		// receive
		try {
			AlarmClock.setAlarm(timeout);

			ret = Message.decode(sock);

			AlarmClock.clearAlarm();

			this.receiver.connPool.put(sockAddr, sock);

			// timeout calculation
			this.receiver.provider.getTimeoutCalculator().updateRTT(dest, (int)(Timer.currentTimeMillis() - start));
		}
		catch (Exception e) {
			Thread.interrupted();

			logger.log(Level.INFO, "Timeout: " + timeout + " msec.");

			// notify statistics collector
			MessagingReporter msgReporter = this.receiver.getMessagingReporter();
			if (msgReporter != null) {
				msgReporter.notifyStatCollectorOfDeletedNode(
						msg.getSource(), dest, msg.getTag());
			}

			throw new IOException("Timeout:" + timeout + " msec.");
		}

		return ret;
	}

	private void adjustLoopbackAddress(InetMessagingAddress dest) {
		// adjust loopback address (e.g. 127.0.0.1) to a real address
		if (dest.getInetAddress().isLoopbackAddress()) {
			dest.setInetAddress(((InetMessagingAddress)this.receiver.getSelfAddress()).getInetAddress());

			logger.log(Level.INFO, "destination is loopback address and adjusted to " + dest);
		}
	}
}
