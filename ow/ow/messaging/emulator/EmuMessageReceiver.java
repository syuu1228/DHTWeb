/*
 * Copyright 2009 Kazuyuki Shudo, and contributors.
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

package ow.messaging.emulator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;

import ow.messaging.ExtendedMessageHandler;
import ow.messaging.Message;
import ow.messaging.MessageHandler;
import ow.messaging.MessageReceiver;
import ow.messaging.MessageSender;
import ow.messaging.MessagingAddress;
import ow.stat.MessagingReporter;
import ow.stat.StatConfiguration;
import ow.stat.StatFactory;
import ow.util.concurrent.SingletonThreadPoolExecutors;
import ow.util.concurrent.ExecutorBlockingMode;

/**
 * A {@link MessageReceiver MessageReceiver} class for distributed environment emulation.
 */
public final class EmuMessageReceiver implements MessageReceiver {
	private final static Logger logger = Logger.getLogger("messaging");

	private static Map<EmuMessagingAddress,EmuMessageReceiver> receiverTable =
		new ConcurrentHashMap<EmuMessagingAddress,EmuMessageReceiver>();

	final EmuMessagingConfiguration config;
	private EmuMessagingAddress selfAddr;
	protected final EmuMessagingProvider provider;
	private final EmuMessageSender singletonSender;

	private final MessagingReporter msgReporter;

	private List<MessageHandler> handlerList = new ArrayList<MessageHandler>();
	private List<ExtendedMessageHandler> extendedHandlerList = new ArrayList<ExtendedMessageHandler>();
	private int numExtendedHandler = 0;

	private long latencyMillis;
	private int latencyNanos;
	private final SleepPeriodMeasure sleepPeriodMeasure;
	final boolean communicationCanFail;

	private static boolean oomPrinted = false;

	protected EmuMessageReceiver(EmuMessagingConfiguration config,
			EmuHostID selfInetAddr, int port, EmuMessagingProvider provider) {
		this.config = config;
		this.selfAddr = new EmuMessagingAddress(selfInetAddr, port);
		this.provider = provider;
		this.singletonSender = new EmuMessageSender(this);

		StatConfiguration conf = StatFactory.getDefaultConfiguration();
		this.msgReporter = StatFactory.getMessagingReporter(conf, this.provider, this.getSender());

		long latencyMicros = config.getAdditionalLatencyMicros();
		if (latencyMicros > 0L) {
			this.latencyMillis = latencyMicros / 1000L;
			this.latencyNanos = (int)(latencyMicros - (1000 * this.latencyMillis)) * 1000;

			this.sleepPeriodMeasure = new SleepPeriodMeasure();
		}
		else {
			this.sleepPeriodMeasure = null;
		}

		this.communicationCanFail = (config.getCommunicationFailureRate() > 0.0) ? true : false;
	}

	public MessagingAddress getSelfAddress() { return this.selfAddr; }
	public void setSelfAddress(String hostname) {
		this.selfAddr = (EmuMessagingAddress)this.provider.getMessagingAddress(
				hostname, this.selfAddr.getPort());
	}

	public int getPort() { return this.selfAddr.getPort(); }

	public MessagingReporter getMessagingReporter() { return this.msgReporter; }

	public void start() {
		receiverTable.put(this.selfAddr, this);
	}

	public synchronized void stop() {
		receiverTable.remove(this.selfAddr);
	}

	public MessageSender getSender() {
		return this.singletonSender;
	}

	public void addHandler(MessageHandler handler) {
		synchronized (this.handlerList) {
			this.handlerList.add(handler);

			try {
				if (this.extendedHandlerList.add((ExtendedMessageHandler)handler)) {
					numExtendedHandler++;
				}
			}
			catch (ClassCastException e) {}
		}
	}

	public void removeHandler(MessageHandler handler) {
		synchronized (this.handlerList) {
			this.handlerList.remove(handler);

			try {
				if (this.extendedHandlerList.remove((ExtendedMessageHandler)handler)) {
					numExtendedHandler--;
				}
			}
			catch (ClassCastException e) {}
		}
	}

	protected static EmuMessageReceiver getReceiver(MessagingAddress dest) {
		EmuMessageReceiver receiver = null;

		receiver = EmuMessageReceiver.receiverTable.get(dest);

		return receiver;
	}

	protected Message processAMessage(final Message msg) {
		// check signature
//		byte[] sig = msg.getSignature();
//		byte[] acceptableSig = this.provider.getMessageSignature();
//		if (!Signature.match(sig, acceptableSig))
//			return null;

		// add latency
		// note: This code can stop a Timer thread.
		// It is better for a Timer to use multiple threads if adding latency.
		if (this.sleepPeriodMeasure != null) {
			try {
				sleepPeriodMeasure.sleep(latencyMillis, latencyNanos);
			}
			catch (InterruptedException e) {/*ignore*/}
		}

		// process the received message
		Message ret = null;

//		synchronized (this.handlerList) {
			for (MessageHandler handler: this.handlerList) {
				try {
					ret = handler.process(msg);
				}
				catch (Throwable e) {
					logger.log(Level.SEVERE, "A MessageHandler threw an Exception.", e);
				}
			}
//		}

		// post-process
		if (numExtendedHandler > 0) {
			Runnable r = new Runnable() {
				public void run() {
//					synchronized (EmuMessageReceiver.this) {
						for (ExtendedMessageHandler handler: EmuMessageReceiver.this.extendedHandlerList) {
							try {
								handler.postProcess(msg);
							}
							catch (Throwable e) {
								logger.log(Level.SEVERE, "A MessageHandler threw an Exception.", e);
							}
						}
//					}	// synchronized (EmuMessageReceiver.this)
				}
			};

			try {
				if (this.config.getUseThreadPool()) {
					ExecutorService ex = SingletonThreadPoolExecutors.getThreadPool(
							ExecutorBlockingMode.NON_BLOCKING, Thread.currentThread().isDaemon());
					ex.submit(r);
				}
				else {
					Thread t = new Thread(r);
					t.setDaemon(false);
					t.setName("EmuMessageReceiver (post-process): " + msg);

					t.start();
				}
			}
			catch (OutOfMemoryError e) {
				try {
					Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
				}
				catch (SecurityException e1) {/* ignore */}

				logger.log(Level.SEVERE, "# of threads: " + Thread.activeCount(), e);

				boolean doesPrint = false;
				synchronized (EmuMessageReceiver.class) {
					if (!EmuMessageReceiver.oomPrinted) { 
						EmuMessageReceiver.oomPrinted = true;
						doesPrint = true;
					}
				}

				if (doesPrint) {
					Thread[] tarray = new Thread[Thread.activeCount()];
					Thread.enumerate(tarray);
					for (Thread t: tarray) if (t != null) System.out.println("Th: " + t.getName());
					System.out.flush();

					// forcibly kill the JVM
					Runtime.getRuntime().halt(1);
				}

				throw e;
			}
		}	// if (numExtendedHandler > 0)

		// add latency
		if (this.sleepPeriodMeasure != null) {
			try {
				sleepPeriodMeasure.sleep(latencyMillis, latencyNanos);
			}
			catch (InterruptedException e) {/*ignore*/}
		}

		// set signature
//		ret.setSignature(acceptableSig);

		return ret;
	}
}
