/*
 * Copyright 2006-2010 National Institute of Advanced Industrial Science
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

package ow.routing.impl;

import java.io.IOException;
import java.io.Serializable;
import java.security.InvalidAlgorithmParameterException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import ow.id.ID;
import ow.id.IDAddressPair;
import ow.messaging.ExtendedMessageHandler;
import ow.messaging.Message;
import ow.messaging.MessageHandler;
import ow.messaging.MessageReceiver;
import ow.messaging.MessageSender;
import ow.messaging.MessagingAddress;
import ow.messaging.MessagingProvider;
import ow.messaging.Tag;
import ow.routing.CallbackOnNodeFailure;
import ow.routing.CallbackOnRoute;
import ow.routing.CallbackResultFilter;
import ow.routing.RoutingAlgorithm;
import ow.routing.RoutingAlgorithmConfiguration;
import ow.routing.RoutingAlgorithmProvider;
import ow.routing.RoutingException;
import ow.routing.RoutingHop;
import ow.routing.RoutingResult;
import ow.routing.RoutingRuntime;
import ow.routing.RoutingService;
import ow.routing.RoutingServiceConfiguration;
import ow.stat.MessagingReporter;
import ow.stat.impl.StatMessageFactory;

/**
 * The super class of all routing drivers.
 * Two types of routing drivers corresponding to iterative and recursive lookup are provided
 * as a subclass of this class.
 *
 * @see ow.routing.impl.IterativeRoutingDriver
 * @see ow.routing.impl.RecursiveRoutingDriver
 */
public abstract class AbstractRoutingDriver implements RoutingRuntime, RoutingService {
	protected final static Logger logger = Logger.getLogger("routing");

	// messages
	private final Message pingMessage;
	private final Message ackMessage;

	// members used by sub-classes
	protected RoutingServiceConfiguration config;
	private MessagingProvider msgProvider;
	protected MessageReceiver receiver;
	protected MessageSender sender;

	private final RoutingAlgorithmProvider algoProvider;
	private final RoutingAlgorithmConfiguration algoConfig;
	protected final boolean adjustLastHop;
	protected final boolean queryToAllContacts;
	protected RoutingAlgorithm algorithm;	// not initialized by constructors

	protected MessagingAddress statCollectorAddress;

	private final IDAddressPair selfIDAddressPair;
	private int selfAddressHashCode;	// to check a change of self address

	private Map<Integer,List<MessageHandler>> handlerTable;
		// holding all MessageHandlers
	private Map<Integer,List<ExtendedMessageHandler>> extendedHandlerTable;
		// holding only ExtendedMessagehandlers
	private RoutingDrvMessageHandler msgHandler;

	protected AbstractRoutingDriver(
			RoutingServiceConfiguration conf,
			MessagingProvider provider, MessageReceiver receiver,
			RoutingAlgorithmProvider algoProvider, RoutingAlgorithmConfiguration algoConf,
			ID selfID)
				throws IOException {
		this.msgProvider = provider;
		this.receiver = receiver;
		this.sender = this.receiver.getSender();

		this.algoProvider = algoProvider;
		this.algoConfig = algoConf;
		this.adjustLastHop = algoConf.adjustRoot();
		this.queryToAllContacts = algoConf.queryToAllContacts();

		this.config = conf;

		// set self ID
		MessagingAddress selfAddr = this.receiver.getSelfAddress();
		int idSizeInByte = algoConf.getIDSizeInByte();

		if (selfID != null) {
			// trim self ID
			selfID = selfID.copy(idSizeInByte);

			this.selfIDAddressPair = IDAddressPair.getIDAddressPair(selfID, selfAddr);
		}
		else {
			this.selfIDAddressPair = IDAddressPair.getIDAddressPair(idSizeInByte, selfAddr);
		}

		this.selfAddressHashCode = selfAddr.hashCode();

		// prepare messages
		this.pingMessage = RoutingDriverMessageFactory.getPingMessage(selfIDAddressPair);
		this.ackMessage = RoutingDriverMessageFactory.getAckMessage(selfIDAddressPair);

		// register message handlers
		prepareHandlers();
	}

	//
	// for RoutingService interface
	//

	public RoutingResult routeToRootNode(ID target, int numRootCandidates)
			throws RoutingException {
		ID[] tgts = { target };

		RoutingResult[] res = this.routeToRootNode(tgts, numRootCandidates);
		if (res == null || res[0] == null) throw new RoutingException();
		return res[0];
	}

	public RoutingResult routeToClosestNode(ID target, int numRootCandidates)
			throws RoutingException {
		ID[] tgts = { target };

		RoutingResult[] res = this.routeToClosestNode(tgts, numRootCandidates);
		if (res == null || res[0] == null) throw new RoutingException();
		return res[0];
	}

	public RoutingResult invokeCallbacksOnRoute(ID target, int numRootCandidates,
			Serializable[] returnedValue,
			CallbackResultFilter filter, int tag, Serializable[] args)
				throws RoutingException {
		ID[] tgts = { target };
		Serializable[][] returnedValues = { returnedValue };
		Serializable[][] argss = { args };

		RoutingResult[] res = this.invokeCallbacksOnRoute(tgts, numRootCandidates,
				returnedValues, filter, tag, argss);
		if (res == null || res[0] == null) throw new RoutingException();
		return res[0];
	}

	public void leave() {
		this.algorithm.reset();
	}

	public synchronized void stop() {
		logger.log(Level.INFO, "RoutingDriver#stop() called.");

		if (this.receiver != null) {
			this.receiver.stop();

			this.receiver = null;
			this.sender = null;
		}

		if (this.algorithm != null) {
			this.algorithm.stop();

			this.algorithm = null;
		}
	}

	public synchronized void suspend() {
		if (this.receiver != null) {
			this.receiver.stop();
		}

		if (this.algorithm != null) {
			this.algorithm.suspend();
		}
	}

	public synchronized void resume() {
		if (this.receiver != null) {
			this.receiver.start();
		}

		if (this.algorithm != null) {
			this.algorithm.resume();
		}
	}

	public RoutingServiceConfiguration getConfiguration() {
		return this.config;
	}

	public MessagingProvider getMessagingProvider() {
		return this.msgProvider;
	}

	/**
	 * Returns a {@link MessageSender MessageSender}.
	 */
	public MessageSender getMessageSender() {	// for both RoutingService and RoutingRuntime
		return this.receiver.getSender();
		// a different instance from the sender which this RoutingDriver uses.
	}

	public MessagingReporter getMessagingReporter() {
		return this.receiver.getMessagingReporter();
	}

	/**
	 * Returns the {@link RoutingAlgorithm RoutingAlgorithm} object.
	 */
	public RoutingAlgorithm getRoutingAlgorithm() {
		return this.algorithm;
	}

	/**
	 * Sets a {@link RoutingAlgorithm RoutingAlgorithm} object to this instance.
	 * Should be called by AbstractRoutingAlgorithm().
	 */
	public RoutingAlgorithm setRoutingAlgorithm(RoutingAlgorithm algo) {
		RoutingAlgorithm old = this.algorithm;

		if (this.algorithm != null) {
			// stop the existing RoutingAlgorithm instance
			this.algorithm.stop();
		}

		this.algorithm = algo;

		return old;
	}

	public void setStatCollectorAddress(MessagingAddress address) {
		this.statCollectorAddress = address;
		this.msgProvider.setMessagingCollectorAddress(address);
	}

	public String routeToString(RoutingHop[] route) {
		if (route == null) return "";

		long timeBase = -1L;
		StringBuilder sb = new StringBuilder();

		sb.append("[");
		for (RoutingHop hop: route) {
			if (timeBase < 0L) { timeBase = hop.getTime(); }

			sb.append("\n ");
			sb.append(hop.getIDAddressPair());
			sb.append(" (");
			sb.append(hop.getTime() - timeBase);
			sb.append(")");
		}
		sb.append("\n]");

		return sb.toString();
	}

	//
	// for RoutingRuntime interface
	//

	/**
	 * An utility method for {@link RoutingRuntime RoutingRuntime} users.
	 * Confirm whether the target is alive or not by sending a PING message.
	 */
	public boolean ping(MessageSender sender, IDAddressPair target) throws IOException {
		Message ret = sender.sendAndReceive(target.getAddress(), this.pingMessage);

		int tag = ret.getTag();
		if (tag == Tag.ACK.getNumber()) {
			this.algorithm.touch(target);
			return true;
		}
		else {
			logger.log(Level.WARNING, "Received message should be ACK, but it is: " + tag);
		}

		if (this.algorithm != null) {
			this.algorithm.fail(target);
		}
		return false;
	}

	//
	// for both of RoutingService and RoutingRuntime interfaces
	//

	public IDAddressPair getSelfIDAddressPair() {
		MessagingAddress currentSelfAddress = this.receiver.getSelfAddress();

		if (currentSelfAddress.hashCode() != this.selfAddressHashCode) {
			// update self MessagingAddress
			this.selfIDAddressPair.setAddressAndRecalculateID(currentSelfAddress);

			this.selfAddressHashCode = currentSelfAddress.hashCode();

			// create a new RoutingAlgorithm instance
			try {
				this.algoProvider.initializeAlgorithmInstance(this.algoConfig, this);
			}
			catch (InvalidAlgorithmParameterException e) {
				// NOTREACHED
				logger.log(Level.SEVERE, "Could not create a RoutingAlgorithm instance.");
			}
		}

		return this.selfIDAddressPair;
	}

	public void addMessageHandler(int tag, MessageHandler handler) {
		{
			if (this.handlerTable == null) {
				this.handlerTable = new HashMap<Integer,List<MessageHandler>>();

				// replace RoutingDrvMessageHandler
				if (this.msgHandler != null)
					this.receiver.removeHandler(this.msgHandler);
				this.msgHandler = new RoutingDrvMessageHandler();
				this.receiver.addHandler(this.msgHandler);
			}

			// add handler
			List<MessageHandler> handlerList = this.handlerTable.get(tag);
			if (handlerList == null) {
				handlerList = new ArrayList<MessageHandler>(1);
				this.handlerTable.put(tag, handlerList);
			}
			handlerList.add(handler);
		}

		if (handler instanceof ExtendedMessageHandler) {
			if (this.extendedHandlerTable == null) {
				this.extendedHandlerTable = new HashMap<Integer,List<ExtendedMessageHandler>>();

				// replace RoutingDrvMessageHandler
				if (this.msgHandler != null)
					this.receiver.removeHandler(this.msgHandler);
				this.msgHandler = new ExtRoutingDrvMessageHandler();
				this.receiver.addHandler(this.msgHandler);
			}

			// add handler
			List<ExtendedMessageHandler> handlerList = this.extendedHandlerTable.get(tag);
			if (handlerList == null) {
				handlerList = new ArrayList<ExtendedMessageHandler>(1);
				this.extendedHandlerTable.put(tag, handlerList);
			}
			handlerList.add((ExtendedMessageHandler)handler);
		}
	}

	//
	// Utilities for callbacks
	//

	private List<CallbackOnRoute> routeCallbackList =
		Collections.synchronizedList(new ArrayList<CallbackOnRoute>(1));

	public void addCallbackOnRoute(CallbackOnRoute callback) {
		this.routeCallbackList.add(callback);
	}

	protected Serializable invokeCallbacks(ID target,
			int tag, Serializable[] args, CallbackResultFilter filter,
			IDAddressPair lastHop, boolean onRootNode) {
		MessagingAddress lastHopAddress = (lastHop != null ? lastHop.getAddress() : null);
		logger.log(Level.INFO, "Invoke " + routeCallbackList.size() + " callbacks. lastHop: " + lastHopAddress
				+ ", onRootNode: " + onRootNode + ", on " + selfIDAddressPair.getAddress());

		Serializable result = null;
		synchronized (this.routeCallbackList) {
			for (CallbackOnRoute cb: this.routeCallbackList) {
				result = cb.process(target, tag, args, filter, lastHop, onRootNode);
			}
		}

		if (filter != null) {
			try {
				result = filter.filter(result);
			}
			catch (Throwable e) {
				logger.log(Level.WARNING, "An Exception thrown in CallbackResultFilter#filter().", e);
			}
		}

		return result;
	}

	private List<CallbackOnNodeFailure> failureCallbackList =
		Collections.synchronizedList(new ArrayList<CallbackOnNodeFailure>(1));

	public void addCallbackOnNodeFailure(CallbackOnNodeFailure callback) {
		this.failureCallbackList.add(callback);
	}

	protected void fail(IDAddressPair failedNode) {
		if (this.algorithm != null) {
			this.algorithm.fail(failedNode);
		}

		for (CallbackOnNodeFailure cb: failureCallbackList) {
			cb.fail(failedNode);
		}
	}

	//
	// Protocol implementation
	//

	/**
	 * Prepare message handlers.
	 */
	private void prepareHandlers() {
		MessageHandler handler;

		// PING
		handler = new MessageHandler() {
			public Message process(Message msg) {
				return AbstractRoutingDriver.this.ackMessage;
			}
		};
		this.addMessageHandler(Tag.PING.getNumber(), handler);

		// REQ_NEIGHBORS (sent by a NodeCollector)
		handler = new MessageHandler() {
			public Message process(Message msg) {
				Serializable[] contents = msg.getContents();
				int num = (Integer)contents[0];

				// set statistics collector address
				MessagingAddress reqSource = msg.getSource().getAddress();
				msgProvider.setMessagingCollectorAddress(reqSource);

				// obtain neighbors
				IDAddressPair[] neighbors = algorithm.rootCandidates(selfIDAddressPair.getID(), num);

				return StatMessageFactory.getRepNeighbors(
						selfIDAddressPair, neighbors);
			}
		};
		this.addMessageHandler(Tag.REQ_NEIGHBORS.getNumber(), handler);
	}

	private class RoutingDrvMessageHandler implements MessageHandler {
		/**
		 * This method implements {@link MessageHandler#process(Message) MessageHandler#process()}.
		 */
		public Message process(Message msg) {
			Message ret = null;

			// call handler
			int tag = msg.getTag();
			List<MessageHandler> handlerList = AbstractRoutingDriver.this.handlerTable.get(tag);
			if (handlerList != null) {
				for (MessageHandler handler: handlerList) {
					try {
						ret = handler.process(msg);
					}
					catch (Throwable e) {
						logger.log(Level.SEVERE, "A MessageHandler#process() threw an Exception.", e);
					}
				}
			}

			return ret;
		}
	}	// class RoutingDrvMessageHandler

	private final class ExtRoutingDrvMessageHandler
			extends RoutingDrvMessageHandler implements ExtendedMessageHandler {
		public void postProcess(Message msg) {
			// call handler
			int tag = msg.getTag();
			List<ExtendedMessageHandler> handlerList = AbstractRoutingDriver.this.extendedHandlerTable.get(tag);
			if (handlerList != null) {
				for (ExtendedMessageHandler handler: handlerList) {
					try {
						handler.postProcess(msg);
					}
					catch (Throwable e) {
						logger.log(Level.SEVERE, "A MessageHandler#postProcess() threw an Exception.", e);
					}
				}
			}

			// notify the routing algorithm
			if (AbstractRoutingDriver.this.algorithm != null) {
				IDAddressPair src = msg.getSource();
				if (src.getID() != null && src.getAddress() != null) {
					AbstractRoutingDriver.this.algorithm.touch(src);
				}
			}
		}
	}	// class ExtRoutingDrvMessageHandler
}
