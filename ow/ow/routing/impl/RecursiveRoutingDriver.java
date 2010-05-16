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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.logging.Level;

import ow.id.ID;
import ow.id.IDAddressPair;
import ow.id.IDAddressRoutingContextTriplet;
import ow.messaging.Message;
import ow.messaging.MessageHandler;
import ow.messaging.MessageReceiver;
import ow.messaging.MessagingAddress;
import ow.messaging.MessagingProvider;
import ow.messaging.Tag;
import ow.routing.CallbackResultFilter;
import ow.routing.RoutingAlgorithmConfiguration;
import ow.routing.RoutingAlgorithmProvider;
import ow.routing.RoutingContext;
import ow.routing.RoutingException;
import ow.routing.RoutingHop;
import ow.routing.RoutingResult;
import ow.routing.RoutingServiceConfiguration;
import ow.stat.MessagingReporter;
import ow.util.Timer;
import ow.util.concurrent.SingletonThreadPoolExecutors;
import ow.util.concurrent.ExecutorBlockingMode;

/**
 * A routing driver which performs recursive forwarding/routing/lookup.
 *
 * @see ow.routing.RoutingServiceProvider#getService(RoutingServiceConfiguration, MessagingProvider, ID, int, long)
 * @see ow.routing.impl.IterativeRoutingDriver
 */
public final class RecursiveRoutingDriver extends AbstractRoutingDriver {
	// messages
	private final Message recAckMessage;

	protected RecursiveRoutingDriver(RoutingServiceConfiguration conf,
			MessagingProvider provider, MessageReceiver receiver,
			RoutingAlgorithmProvider algoProvider, RoutingAlgorithmConfiguration algoConf,
			ID selfID)
				throws IOException {
		super(conf, provider, receiver, algoProvider, algoConf, selfID);

		// prepare messages
		this.recAckMessage =
			RoutingDriverMessageFactory.getRecAckMessage(getSelfIDAddressPair());

		// register message handlers
		prepareHandlers();
	}

	public RoutingResult[] routeToRootNode(ID[] target, int numRootCandidates) {
		return route(
				super.adjustLastHop,
				Tag.REC_ROUTE_NONE,
				target, numRootCandidates,
				null, null, -1, null,
				null);
	}

	public RoutingResult[] routeToClosestNode(ID[] target, int numRootCandidates) {	// for RoutingRuntime interface
		return route(
				false,
				Tag.REC_ROUTE_NONE,
				target, numRootCandidates,
				null, null, -1, null,
				null);
	}

	public RoutingResult[] invokeCallbacksOnRoute(ID[] target, int numRootCandidates,
			Serializable[][] returnedValueContainer,
			CallbackResultFilter filter, int tag, Serializable[][] args) {
		return route(
				super.adjustLastHop,
				Tag.REC_ROUTE_INVOKE,
				target, numRootCandidates,
				filter, returnedValueContainer, tag, args,
				null);
	}

	public RoutingResult join(MessagingAddress initialContact)
			throws RoutingException {
		ID[] tgts = { this.getSelfIDAddressPair().getID() };

		RoutingResult[] res = route(
				super.adjustLastHop,
				Tag.REC_ROUTE_JOIN,
				tgts, this.config.getNumOfRootCandidatesRequestedWhenJoining(),
				null, null, -1, null,
				initialContact);			// joinInitialContact

		if (res == null || res[0] == null) throw new RoutingException();

		algorithm.join(res[0].getRootCandidates());
			// the algorithm instance performs the joining process 

		return res[0];
	}

	private final static int TAG_NULL = -1;

	private Map<ID,Message> routeResultMsgTable = new HashMap<ID,Message>();

	private RoutingResult[] route(
			boolean adjustLastHop,
			Tag msgType,
			ID[] target, int numRootCandidates,
			CallbackResultFilter filter, Serializable[][] resultingCallbackResult, int callbackTag, Serializable[][] callbackArgs,
			MessagingAddress joinInitialContact) {
		List<IDAddressPair>[] closestNodes = new List/*<IDAddressPair>*/[target.length];
		IDAddressPair[] blackList = null;

		if (numRootCandidates < 1) numRootCandidates = 1;

		// notify messaging visualizer
		if (msgType != Tag.REC_ROUTE_JOIN) {
			MessagingReporter msgReporter = receiver.getMessagingReporter();

			msgReporter.notifyStatCollectorOfEmphasizeNode(
					this.getSelfIDAddressPair(), this.getSelfIDAddressPair().getID());
			msgReporter.notifyStatCollectorOfMarkedID(
					this.getSelfIDAddressPair(), target, 0);
		}

		// forward
		RoutingHop[] initialRoute = new RoutingHop[1];
		initialRoute[0] = RoutingHop.newInstance(this.getSelfIDAddressPair());
		RoutingContext[] initialRoutingContext = new RoutingContext[target.length];

		if (msgType == Tag.REC_ROUTE_JOIN) {
			initialRoutingContext = null;
				// routing context should be calculated on joinInitialContact

			for (int i = 0; i < target.length; i++) {
				closestNodes[i] = new ArrayList<IDAddressPair>();
				closestNodes[i].add(IDAddressPair.getIDAddressPair(null, joinInitialContact));
			}
		}
		else {	// msgType == Tag.REC_ROUTE_NONE || msgType == Tag.REC_ROUTE_INVOKE
			for (int i = 0; i < target.length; i++) {
				initialRoutingContext[i] = algorithm.initialRoutingContext(target[i]);
				closestNodes[i] = new ArrayList<IDAddressPair>();

				IDAddressPair[] nodes = algorithm.closestTo(target[i], this.config.getNumOfClosestNodesRequested(), initialRoutingContext[i]);
				for (IDAddressPair p: nodes) closestNodes[i].add(p);
			}
		}

		synchronized (this.routeResultMsgTable) {
			// put a null Message as a marker
			Message nullMsg = new Message(null, TAG_NULL);
			for (int i = 0; i < target.length; i++) {
				this.routeResultMsgTable.put(target[i], nullMsg);
			}
		}

		forwardOrReturnResult(adjustLastHop, false, msgType.getNumber(), null,
				closestNodes, initialRoutingContext,
				target, numRootCandidates, this.getSelfIDAddressPair(), config.getTTL(),
				filter, callbackTag, callbackArgs,
				initialRoute, blackList);

		// wait for REC_RESULT messages
		RoutingResult[] ret = new RoutingResult[target.length];
		Set<Integer> failedIndexSet = new HashSet<Integer>();
		long sleepLimit = Timer.currentTimeMillis() + config.getRoutingTimeout();

		waitForResults:
		while (true) {
			Message resultMsg = null;

			synchronized (this.routeResultMsgTable) {
				retrieveMessage:
				while (true) {
					// peek a received message
					for (int i = 0; i < target.length; i++) {
						if (ret[i] == null) 
							resultMsg = this.routeResultMsgTable.get(target[i]);

						if (resultMsg != null && resultMsg.getTag() != TAG_NULL) {
//System.out.println("RESULT msg received: " + target[i].toString().substring(0, 4) + "..");
							this.routeResultMsgTable.remove(target[i]);
							break retrieveMessage;
						}

						resultMsg = null;
					}

					// sleep
					long sleepPeriod = sleepLimit - Timer.currentTimeMillis();
					if (sleepPeriod <= 0L) {
						for (ID id: target)
							this.routeResultMsgTable.remove(id);	// clean up

						break waitForResults;
					}

					try {
						this.routeResultMsgTable.wait(sleepPeriod);
					}
					catch (InterruptedException e) {
						sleepLimit = Timer.currentTimeMillis();
					}
				}
			}

			Serializable[] contents = resultMsg.getContents();
			boolean succeed = (Boolean)contents[1];
			ID[] tgt = (ID[])contents[2];
			RoutingResult[] result = (RoutingResult[])contents[3];
			Serializable[] callbackResult = (Serializable[])contents[4];

			// notify the routing algorithm of nodes on the route
			for (RoutingResult res: result) {
				RoutingHop[] route = res.getRoute();

				IDAddressPair selfIDAddress = this.getSelfIDAddressPair();
				for (RoutingHop h: route) {
					IDAddressPair p = h.getIDAddressPair();
					if (p == null || selfIDAddress.equals(p)) break;
					algorithm.touch(p);
				}
			}

			// prepare RoutingResult and callback results
			for (int i = 0; i < tgt.length; i++) {
				boolean match = false;

				for (int j = 0; j < target.length; j++) {
					if (tgt[i].equals(target[j])) {
						match = true;
						ret[j] = result[i];

						if (!succeed) failedIndexSet.add(j);

						if (resultingCallbackResult != null && resultingCallbackResult[j] != null) {
							resultingCallbackResult[j][0] = callbackResult[i];
						}
					}
				}

				if (!match) {
					logger.log(Level.WARNING,
							"Received REC_RESULT message is not for an expected target: " + tgt[i]);
				}
			}

			boolean filled = true;
			for (int i = 0; i < target.length; i++) {
				if (ret[i] == null) filled = false;
			}
			if (filled) break;
		}	// while (true)

		Set<ID> noResultTarget = new HashSet<ID>();
		for (int i = 0; i < target.length; i++) {
			if (ret[i] == null) {
				noResultTarget.add(target[i]);
			}
		}
		if (!noResultTarget.isEmpty()) {
			StringBuilder sb = new StringBuilder();
			for (ID id: noResultTarget) {
				sb.append(" ").append(id);
			}

			logger.log(Level.WARNING,
					"Could not receive a REC_RESULT message for the target" + sb.toString());
		}

		for (int index: failedIndexSet) {
			ret[index] = null;
		}

		return ret;
	}

	/**
	 * Prepare message handlers for received messages.
	 */
	private void prepareHandlers() {
		MessageHandler handler;

		// REC_{ROUTE,TERMINATE}_{NONE,INVOKE,JOIN}
		handler = new MessageHandler() {
			public Message process(Message msg) {
				Message ret = RecursiveRoutingDriver.this.recAckMessage;

				// parse the Message
				IDAddressPair initiator;
				ID[] target;
				int numRootCandidates = 1;
				RoutingContext[] routingContext = null;
				int ttl = -1;
				boolean adjustLastHop = false;
				IDAddressPair[] blackList;
				CallbackResultFilter filter = null;
				int callbackTag = -1;
				Serializable[][] callbackArgs = null;
				RoutingHop[] route;

				boolean terminate;

				// parse the incoming message
				Serializable[] contents = msg.getContents();
				if (msg.getTag() == Tag.REC_ROUTE_NONE.getNumber()) {
					blackList = (IDAddressPair[])contents[0];
					target = (ID[])contents[1];
					numRootCandidates = (Integer)contents[2];
					routingContext = (RoutingContext[])contents[3];
					initiator = (IDAddressPair)contents[4];
					ttl = (Integer)contents[5];
					adjustLastHop = (Boolean)contents[6];
					route = (RoutingHop[])contents[7];

					terminate = false;
				}
				else if (msg.getTag() == Tag.REC_ROUTE_INVOKE.getNumber()) {
					blackList = (IDAddressPair[])contents[0];
					target = (ID[])contents[1];
					numRootCandidates = (Integer)contents[2];
					routingContext = (RoutingContext[])contents[3];
					initiator = (IDAddressPair)contents[4];
					ttl = (Integer)contents[5];
					adjustLastHop = (Boolean)contents[6];
					filter = (CallbackResultFilter)contents[7];
					callbackTag = (Integer)contents[8];
					callbackArgs = (Serializable[][])contents[9];
					route = (RoutingHop[])contents[10];

					terminate = false;
				}
				else if (msg.getTag() == Tag.REC_ROUTE_JOIN.getNumber()) {
					blackList = (IDAddressPair[])contents[0];
					initiator = (IDAddressPair)contents[1];
					target = new ID[1]; target[0] = initiator.getID();
					numRootCandidates = (Integer)contents[2];
					routingContext = (RoutingContext[])contents[3];
					ttl = (Integer)contents[4];
					adjustLastHop = (Boolean)contents[5];
					route = (RoutingHop[])contents[6];

					terminate = false;
				}
				else if (msg.getTag() == Tag.REC_TERMINATE_NONE.getNumber()) {
					blackList = (IDAddressPair[])contents[0];
					target = (ID[])contents[1];
					numRootCandidates = (Integer)contents[2];
					initiator = (IDAddressPair)contents[3];
					ttl = (Integer)contents[4];
					route = (RoutingHop[]) contents[5];

					terminate = true;	// this node is the responsible node
				}
				else if (msg.getTag() == Tag.REC_TERMINATE_INVOKE.getNumber()) {
					blackList = (IDAddressPair[])contents[0];
					target = (ID[])contents[1];
					numRootCandidates = (Integer)contents[2];
					initiator = (IDAddressPair)contents[3];
					ttl = (Integer)contents[4];
					filter = (CallbackResultFilter)contents[5];
					callbackTag = (Integer)contents[6];
					callbackArgs = (Serializable[][])contents[7];
					route = (RoutingHop[])contents[8];

					terminate = true;	// this node is the responsible node
				}
				else if (msg.getTag() == Tag.REC_TERMINATE_JOIN.getNumber()){
					blackList = (IDAddressPair[])contents[0];
					initiator = (IDAddressPair)contents[1];
					target = new ID[1]; target[0] = initiator.getID();
					numRootCandidates = (Integer)contents[2];
					ttl = (Integer)contents[3];
					route = (RoutingHop[])contents[4];

					terminate = true;	// this node is the responsible node
				}
				else {
					logger.log(Level.WARNING, "Unexpected type of message: " + Tag.getNameByNumber(msg.getTag()));
					return ret;
				}

				// add this node itself to the resulting route
				RoutingHop[] lastRoute = route;
				route = new RoutingHop[lastRoute.length + 1];
				System.arraycopy(lastRoute, 0, route, 0, lastRoute.length);
				route[route.length - 1] = RoutingHop.newInstance(getSelfIDAddressPair());

				// remove nodes in blacklist from routing table
				// Note: this removes nodes which this node itself has not contacted
				// and prone to be abused by a malicious node.
				if (blackList != null) {
					for (IDAddressPair p: blackList) {
						logger.log(Level.INFO, "REC_*calls fail: " + p.getAddress() + " on " + getSelfIDAddressPair().getAddress());

						fail(p);	// AbstractRoutingDriver#fail()
					}
				}

				// calculate routing context if null
				if (routingContext == null)
					routingContext = new RoutingContext[target.length];

				for (int i = 0; i < target.length; i++) {
					if (routingContext[i] == null) {
						routingContext[i] = algorithm.initialRoutingContext(target[i]);
					}
				}

				// calculate next hop
				List<IDAddressPair>[] closestNodes = null;
				if (!terminate) {
					closestNodes = new List/*IDAddressPair*/[target.length];
					for (int i = 0; i < target.length; i++) {
						closestNodes[i] = new ArrayList<IDAddressPair>();
						IDAddressPair[] nodes = algorithm.closestTo(
								target[i], config.getNumOfClosestNodesRequested(), routingContext[i]);
						for (IDAddressPair p: nodes) closestNodes[i].add(p);
					}
				}

				// forward
				Forwarder f = new Forwarder(
						adjustLastHop, terminate, msg.getTag(), msg.getSource(),
						closestNodes, routingContext,
						target, numRootCandidates, initiator, ttl, filter, callbackTag, callbackArgs, route,
						blackList);

				if (config.getUseThreadPool()) {
					ExecutorService ex = SingletonThreadPoolExecutors.getThreadPool(
							ExecutorBlockingMode.NON_BLOCKING, Thread.currentThread().isDaemon());
					ex.submit((Callable<Boolean>)f);
				}
				else {
					Thread t = new Thread(f);
					t.setName("Forwarder");
					t.setDaemon(Thread.currentThread().isDaemon());
					t.start();
				}

				return ret;
			}
		};
		addMessageHandler(Tag.REC_ROUTE_NONE.getNumber(), handler);
		addMessageHandler(Tag.REC_ROUTE_INVOKE.getNumber(), handler);
		addMessageHandler(Tag.REC_ROUTE_JOIN.getNumber(), handler);
		addMessageHandler(Tag.REC_TERMINATE_NONE.getNumber(), handler);
		addMessageHandler(Tag.REC_TERMINATE_INVOKE.getNumber(), handler);
		addMessageHandler(Tag.REC_TERMINATE_JOIN.getNumber(), handler);

		// REC_RESULT
		handler = new MessageHandler() {
			public Message process(Message msg) {
				Serializable contents[] = msg.getContents();
				IDAddressPair[] blackList = (IDAddressPair[])contents[0];
				ID[] target = (ID[])contents[2];
//System.out.println("REC_RESULT message received: " + target[0].toString().substring(0, 4) + "..");

				synchronized (routeResultMsgTable) {
					if (target != null && target.length > 0) {
						for (ID id: target) {
							Message nullMsg = routeResultMsgTable.get(id);
							if (nullMsg != null && nullMsg.getTag() == TAG_NULL) {
								routeResultMsgTable.put(id, msg);
							}
						}

						routeResultMsgTable.notifyAll();
					}
				}

				// remove nodes in blacklist from routing table
				// Note: this removes nodes which this node itself has not contacted
				// and prone to be abused by a malicious node.
				if (blackList != null) {
					for (IDAddressPair p: blackList) {
						logger.log(Level.INFO, "REC_RESULT calls fail: " + p.getAddress()
								+ " on " + getSelfIDAddressPair().getAddress());

						fail(p);	// AbstractRoutingDriver#fail()
					}
				}

				// notify the routing algorithm
				//algorithm.touch(msg.getSource());
					// not necessary to call touch() because
					// the last entry in the returned route is same as msg.getSource()

				return null;
			}
		};
		addMessageHandler(Tag.REC_RESULT.getNumber(), handler);
	}

	private boolean forwardOrReturnResult(
			boolean adjustLastHop,
			boolean terminate,
			final int oldMsgTag, final IDAddressPair lastHop,
			final List<IDAddressPair>[] closestNodes, final RoutingContext[] routingContext,
			final ID[] target, final int numRootCandidates, final IDAddressPair initiator, final int ttl,
			final CallbackResultFilter filter, final int callbackTag, final Serializable[][] callbackArgs,
			final RoutingHop[] route,
			IDAddressPair[] blackList) {
//System.out.print("forRetResult called:");
//for (ID id: target) System.out.print(" " + id.toString().substring(0, 4) + "..");
//System.out.println();
//System.out.println("  on " + getSelfIDAddressPair().getAddress());
//System.out.println("  msg: " + Tag.getNameByNumber(oldMsgTag));
//System.out.println("  adj: " + adjustLastHop + ", term: " + terminate);
//System.out.flush();

		Set<IDAddressPair> blackListSet = new HashSet<IDAddressPair>();
		if (blackList != null) {
			for (IDAddressPair a: blackList) {
				blackListSet.add(a);
			}
		}

		boolean[] terminates = new boolean[target.length];
		for (int i = 0; i < target.length; i++) terminates[i] = terminate;

		boolean succeed = true;
		Message newMsg;
		boolean[] forwarded = new boolean[target.length];
		for (int i = 0; i < forwarded.length; i++) forwarded[i] = false;

		// check if this node is the responsible node
		IDAddressPair[][] rootCandidates = null;
		if (terminate) {
			rootCandidates = new IDAddressPair[target.length][];
			for (int i = 0; i < target.length; i++)
				rootCandidates[i] = algorithm.rootCandidates(target[i], numRootCandidates);

			for (int i = 0; i < target.length; i++) {
				if (rootCandidates[i][0] != null
						&& !getSelfIDAddressPair().equals(rootCandidates[i][0].getIDAddressPair())
						&& !(oldMsgTag == Tag.REC_TERMINATE_JOIN.getNumber() && initiator.equals(rootCandidates[i][0]))) {
					adjustLastHop = true;
					terminates[i] = false;
						// It is safe to set adjustLastHop as true
						// because terminate is true and the last hop has been already adjusted.

					logger.log(Level.WARNING/*INFO*/,
							"This node is not responsible node for "	+ target[i].toString(-1)
							+ "... adjusted from "
							+ this.getSelfIDAddressPair().getAddress() + " (" + this.getSelfIDAddressPair().getID().toString(-1)
							+ ") to "
							+ rootCandidates[i][0].getAddress() + " (" + rootCandidates[i][0].getID().toString(-1) + ").");
				}
				else {
					rootCandidates[i] = null;	// clear
				}
			}
		}

		// TTL check
		if (!terminate && ttl < 0) {
			StringBuilder sb = new StringBuilder();
			sb.append("TTL expired (target ");
			for (ID t: target) { sb.append(" ").append(t.toString().substring(0, 6)); }
			sb.append("):");
			for (RoutingHop h: route) {
				if (h == null) break;

				sb.append(" ");
				sb.append(h.getIDAddressPair().getAddress().getHostname());
			}

			logger.log(Level.WARNING, sb.toString(), new Throwable());

			terminate = true;
			succeed = false;
		}

		IDAddressPair[] nextHops = new IDAddressPair[target.length];

		forward:
		while (true) {
			if (terminate) break;	// in case of REC_TERMINATE_...

			do {
				// fork
				Set<IDAddressPair> contactSet = new HashSet<IDAddressPair>();
				boolean allContactsAreNull = true;
				boolean aContactIsNull = false;

				for (int i = 0; i < target.length; i++) {
					if (closestNodes[i] == null || closestNodes[i].size() <= 0)
						nextHops[i] = null;
					else {
						nextHops[i] = closestNodes[i].get(0);

						RoutingContext cxt = null;
						try {
							cxt = ((IDAddressRoutingContextTriplet)nextHops[i]).getRoutingContext();
						}
						catch (ClassCastException e) {}

						if (nextHops[i].getAddress().equals(this.getSelfIDAddressPair().getAddress())) {
							// next hop is this node itself
							if (cxt == null	// not Koorde
									|| (routingContext != null && cxt.equals(routingContext[i]))) {
								nextHops[i] = null;
							}
						}
						else if (nextHops[i].getAddress().equals(initiator.getAddress())) {
							// next hop is initiator of routing
							if (cxt == null) {	// not Koorde
								nextHops[i] = null;

								if (oldMsgTag == Tag.REC_ROUTE_JOIN.getNumber()
										|| oldMsgTag == Tag.REC_TERMINATE_JOIN.getNumber()) {
									logger.log(Level.WARNING, "Next hop is the joining node "
											+ initiator.getAddress()
											+ ". RoutingAlgorithm#touch() has been called too early?");
								}
							}
						}
						else if (blackListSet.contains(nextHops[i].getAddress())) {
							// next hop is in the black list
							closestNodes[i].remove(0);
							continue;
						}
					}

					if (nextHops[i] != null) {
						contactSet.add(nextHops[i].getIDAddressPair());
						allContactsAreNull = false;
					}
					else {
						contactSet.add(null);
						aContactIsNull = true;
					}
				}

				if (allContactsAreNull) {	// this node is the responsible node
					break forward;
				}

//System.out.println("tag: " + Tag.getNameByNumber(oldMsgTag));
//System.out.print("contactSet:");
//for (IDAddressPair p: contactSet) System.out.print(" " + (p == null ? "(null)" : p.getAddress()));
//System.out.println();
//System.out.flush();
				if (contactSet.size() > 1 || aContactIsNull) {	// fork
//System.out.println("fork on " + getSelfIDAddressPair().getAddress());
					Set<Forwarder> forkedForwarder = new HashSet<Forwarder>();
					List<Integer> contactIndexList = new ArrayList<Integer>();

					for (IDAddressPair c: contactSet) {
						contactIndexList.clear();

						if (c == null) { 
							for (int i = 0; i < target.length; i++)
								if (nextHops[i] == null) contactIndexList.add(i);
						}
						else {
							for (int i = 0; i < target.length; i++)
								if (c.equals(nextHops[i])) contactIndexList.add(i);
						}

						int nTgts = contactIndexList.size();
						final ID[] forkedTarget = new ID[nTgts];
						final List<IDAddressPair>[] forkedClosestNodes = new List/*<IDAddressPair>*/[nTgts];
						for (int i = 0; i < nTgts; i++) {
							int index = contactIndexList.get(i);
							forkedTarget[i] = target[index];
							forkedClosestNodes[i] = closestNodes[index];
						}
						Serializable[][] forkedCallbackArgs = null;
						if (callbackArgs != null) {
							forkedCallbackArgs = new Serializable[nTgts][];
							for (int i = 0; i < nTgts; i++) {
								int index = contactIndexList.get(i);
								forkedCallbackArgs[i] = callbackArgs[index];
							}
						}

						Forwarder f = new Forwarder(adjustLastHop,
								terminate,
								oldMsgTag, lastHop,
								forkedClosestNodes, routingContext,
								forkedTarget, numRootCandidates, initiator, ttl,
								filter, callbackTag, forkedCallbackArgs,
								route,
								blackList);
						forkedForwarder.add(f);
					}

					// execute
					boolean ret = true;

					if (config.getUseThreadPool()) {
						Set<Future<Boolean>> fSet = new HashSet<Future<Boolean>>();
						Forwarder firstForwarder = null;

						ExecutorService ex = SingletonThreadPoolExecutors.getThreadPool(
								ExecutorBlockingMode.REJECTING, Thread.currentThread().isDaemon());

						for (Forwarder forwarder: forkedForwarder) {
							if (firstForwarder == null) { firstForwarder = forwarder; continue; }

							try {
								Future<Boolean> f = ex.submit((Callable<Boolean>)forwarder);
								fSet.add(f);
							}
							catch (RejectedExecutionException e) {
								// invoke directly if rejected
								// Note that this is required to avoid deadlocks
								ret &= forwarder.call();
							}
						}

						ret &= firstForwarder.call();	// direct invocation

						for (Future<Boolean> f: fSet) {
							try {
								ret &= f.get();
							}
							catch (Exception e) {/*ignore*/}
						}
					}
					else {
						Set<Thread> tSet = new HashSet<Thread>();
						for (Runnable r: forkedForwarder) {
							Thread t = new Thread(r);
							t.setName("Forwarder");
							t.setDaemon(Thread.currentThread().isDaemon());
							tSet.add(t);
							t.start();
						}
						for (Thread t: tSet) {
							try { t.join(); } catch (InterruptedException e) {/*ignore*/}
						}
						for (Forwarder f: forkedForwarder) {
							ret &= f.getResult();
						}
					}

					return ret;
				}	// if (contactSet.size() > 1)	// fork

//System.out.println("forward or reply on " + getSelfIDAddressPair().getAddress());
				RoutingContext[] nextContext = new RoutingContext[target.length];
				try {
					for (int i = 0; i < target.length; i++) {
						nextContext[i] =
							((IDAddressRoutingContextTriplet)nextHops[i]).getRoutingContext();
							// nextContext[i] is null when joining
					}
				}
				catch (ClassCastException e) { nextContext = null; }

				IDAddressPair nextHop = nextHops[0].getIDAddressPair();
					// assert: all nextHops[i].getIDAddressPair() is the same value
//System.out.println("On " + getSelfIDAddressPair().getAddress() + ", nextHop: " + nextHop);
//System.out.println("  target: " + target[0]);

				// in case of join, skip the next hop if it is the destination
				if ((oldMsgTag == Tag.REC_ROUTE_JOIN.getNumber() || oldMsgTag == Tag.REC_TERMINATE_JOIN.getNumber())
						&& target[0].equals(nextHop.getID()))
					continue;

				// black list check
				if (nextHop.getID() != null)	// null in case of joining
					if (blackListSet.contains(nextHop.getIDAddressPair()))
						continue;	// next hop is in the black list

				// prepare a Message
				if (oldMsgTag == Tag.REC_ROUTE_NONE.getNumber()) {
					newMsg = RoutingDriverMessageFactory.getRecRouteNoneMessage(
							getSelfIDAddressPair(),
							target, numRootCandidates, nextContext,
							initiator, ttl - 1, adjustLastHop, route);
				}
				else if (oldMsgTag == Tag.REC_ROUTE_INVOKE.getNumber()) {
					newMsg = RoutingDriverMessageFactory.getRecRouteInvokeMessage(
							getSelfIDAddressPair(),
							target, numRootCandidates, nextContext,
							initiator, ttl - 1, adjustLastHop,
							filter, callbackTag, callbackArgs,
							route);
				}
				else {	// REC_ROUTE_JOIN
					newMsg = RoutingDriverMessageFactory.getRecRouteJoinMessage(
							getSelfIDAddressPair(),
							initiator, numRootCandidates, nextContext,
							ttl - 1, adjustLastHop, route);
				}
				RoutingDriverMessageFactory.setBlackList(newMsg, blackList);

				try {
					Message ack = sender.sendAndReceive(nextHop.getAddress(), newMsg);
						// throws IOException
//System.out.println("On " + getSelfIDAddressPair().getAddress() + ", forwarded " + Tag.getNameByNumber(oldMsgTag) + " from " + getSelfIDAddressPair().getAddress() + " to " + nextHop.getAddress());

					// fill ID of nextHop
					if (nextHop.getID() == null) nextHop.setID(ack.getSource().getID());
					for (int i = 0; i < target.length; i++) {
						if (nextHops[i].getID() == null) {
							// this is the case in the first iteration of joining
							nextHops[i].setID(ack.getSource().getID());
						}
					}

					// notify the routing algorithm
					if (algorithm != null) {
						algorithm.touch(ack.getSource());
					}

					if (ack.getTag() == Tag.REC_ACK.getNumber()) {
						for (int i = 0; i < forwarded.length; i++) forwarded[i] = true;

						break forward;
					}
					else {
						logger.log(Level.SEVERE, "Received message is not REC_ACK.");
					}
				}
				catch (IOException e) {
//System.out.println("  failed.");
					// sending failure and try the next node
					logger.log(Level.WARNING, "Failed to forward a request to "
							+ nextHop.getAddress()
							+ " on " + getSelfIDAddressPair().getAddress(), e);
				}

				// fail to send/receive
				if (nextHop.getID() != null) {	// nextHop.getID() is null when joining
					super.fail(nextHop);

					if (blackList != null) {
						IDAddressPair[] oldBlackList = blackList;
						blackList = new IDAddressPair[oldBlackList.length + 1];
						System.arraycopy(oldBlackList, 0, blackList, 0, oldBlackList.length);
					}
					else {
						blackList = new IDAddressPair[1];
					}
					blackList[blackList.length - 1] = nextHop;

					blackListSet.add(nextHop);

					logger.log(Level.INFO, nextHop.getAddress() + " is added to blacklist on " + this.getSelfIDAddressPair().getAddress());
				}
			} while (false);

			// shift closestNodes[i]
			for (int i = 0; i < target.length; i++) {
				if (closestNodes[i] == null) continue;

				if (closestNodes[i].size() <= 1)
					closestNodes[i] = null;
				else {
					closestNodes[i].remove(0);
				}
			}
		}	// forward: while (true)

		// send terminating message
		if (!forwarded[0] && adjustLastHop) {
//System.out.println("try to adjust on " + getSelfIDAddressPair().getAddress());
			List<IDAddressPair>[] adjustedLastHops = new List/*<IDAddressPair>*/[target.length];

			for (int i = 0; i < target.length; i++) {
				if (terminates[i]) {
					adjustedLastHops[i] = null;
				}
				else {
					adjustedLastHops[i] = new LinkedList<IDAddressPair>();

					IDAddressPair[] l;
					if (rootCandidates != null && rootCandidates[i] != null)
						l = rootCandidates[i];
					else
						l = algorithm.adjustRoot(target[i]);

					for (IDAddressPair p: l)
						if (p != null) adjustedLastHops[i].add(p);
				}
			}

			Set<IDAddressPair> nextHopSet = new HashSet<IDAddressPair>();
			List<Integer> nextHopIndexList = new ArrayList<Integer>();

			while (true) {
				nextHops = new IDAddressPair[target.length];
				nextHopSet.clear();

				for (int i = 0; i < target.length; i++) {
					if (adjustedLastHops[i] == null || adjustedLastHops[i].isEmpty()
							|| forwarded[i])
						continue;

					do {
						nextHops[i] = null;
						try {
							nextHops[i] = adjustedLastHops[i].remove(0);
						}
						catch (IndexOutOfBoundsException e) {/*ignore*/}
					} while (nextHops[i] != null		// retry condition
							&& (blackListSet.contains(nextHops[i].getIDAddressPair())
									|| (oldMsgTag == Tag.REC_ROUTE_JOIN.getNumber() && initiator.equals(nextHops[i]))));

					if (nextHops[i] != null)
						nextHopSet.add(nextHops[i].getIDAddressPair());
				}

				if (nextHopSet.isEmpty()) break;

				for (IDAddressPair nextHop: nextHopSet) {
					nextHopIndexList.clear();

					for (int i = 0; i < target.length; i++) {
						if (nextHop.equals(nextHops[i])) {
							nextHopIndexList.add(i);
						}
					}

					ID[] forkedTarget = new ID[nextHopIndexList.size()];
					for (int i = 0; i < nextHopIndexList.size(); i++) {
						forkedTarget[i] = target[nextHopIndexList.get(i)];
					}

					if (oldMsgTag == Tag.REC_ROUTE_NONE.getNumber()
							|| oldMsgTag == Tag.REC_TERMINATE_NONE.getNumber()) {
						newMsg = RoutingDriverMessageFactory.getRecTerminateNoneMessage(
								getSelfIDAddressPair(),
								forkedTarget, numRootCandidates,
								initiator, ttl - 1, route);
					}
					else if (oldMsgTag == Tag.REC_ROUTE_INVOKE.getNumber()
							|| oldMsgTag == Tag.REC_TERMINATE_INVOKE.getNumber()) {
						newMsg = RoutingDriverMessageFactory.getRecTerminateInvokeMessage(
								getSelfIDAddressPair(),
								forkedTarget, numRootCandidates,
								initiator, ttl - 1, filter, callbackTag, callbackArgs,
								route);
					}
					else { // REC_ROUTE_JOIN or REC_TERMINATE_JOIN
						newMsg = RoutingDriverMessageFactory.getRecTerminateJoinMessage(
								getSelfIDAddressPair(),
								initiator, numRootCandidates, ttl - 1,
								route);
					}
					RoutingDriverMessageFactory.setBlackList(newMsg, blackList);

					try {
						Message ack = sender.sendAndReceive(nextHop.getAddress(), newMsg);
//System.out.println("forwarded (terminate) from " + getSelfIDAddressPair().getAddress() + " to " + nextHop.getAddress());

						// notify the routing algorithm
						algorithm.touch(ack.getSource());

						// clear contact lists corresponding to the succeeded contact
						for (int i: nextHopIndexList) {
							adjustedLastHops[i] = null;
						}

						if (ack.getTag() == Tag.REC_ACK.getNumber()) {
							// succeed
							for (int i: nextHopIndexList) forwarded[i] = true;

							continue;
						}
						else {
							logger.log(Level.SEVERE, "Received message is not REC_ACK.");
						}
					}
					catch (IOException e) {
						logger.log(Level.WARNING, "Failed to forward a request to "
								+ nextHop.getAddress()
								+ " on " + getSelfIDAddressPair().getAddress(), e);
					}

					// failed to send/receive
					super.fail(nextHop);

					if (blackList != null) {
						IDAddressPair[] oldBlackList = blackList;
						blackList = new IDAddressPair[oldBlackList.length + 1];
						System.arraycopy(oldBlackList, 0, blackList, 0, oldBlackList.length);
					}
					else {
						blackList = new IDAddressPair[1];
					}
					blackList[blackList.length - 1] = nextHop;

					succeed = false;
				}	// for (IDAddressPair nextHop: nextHopSet)
			}	//	while (true)
		}	// if (!forwarded[0] && adjustLastHop)

		// notify the routing algorithm
		if (lastHop != null)
			algorithm.touch(lastHop);	// source of message
			// this is an additional call to touch() compared with iterative lookup
		if (!this.getSelfIDAddressPair().equals(initiator))
			algorithm.touch(initiator);			// initiator of message

		// message dependent processes
		Serializable[] callbackResult = new Serializable[target.length];
		if (oldMsgTag == Tag.REC_ROUTE_INVOKE.getNumber()
				|| oldMsgTag == Tag.REC_TERMINATE_INVOKE.getNumber()) {
			// invoke callbacks
			for (int i = 0; i < target.length; i++) {
				callbackResult[i] = invokeCallbacks(target[i], callbackTag, callbackArgs[i], filter, lastHop, !forwarded[i]);
				if (callbackResult[i] != null) {
					logger.log(Level.INFO, "A callback returned non-null object: " + callbackResult[i]);
				}
			}
		}
		else if (oldMsgTag == Tag.REC_ROUTE_JOIN.getNumber()
				|| oldMsgTag == Tag.REC_TERMINATE_JOIN.getNumber()) {
			final IDAddressPair copiedJoiningNode = initiator;
			final IDAddressPair copiedLastHop = lastHop;
			final boolean[] copiedForwarded = new boolean[forwarded.length];
			System.arraycopy(forwarded, 0, copiedForwarded, 0, copiedForwarded.length);

			Runnable r = new Runnable() {
				public void run() {
					for (int i = 0; i < target.length; i++)
						algorithm.join(copiedJoiningNode, copiedLastHop, !copiedForwarded[i]);
				}
			};

			try {
				if (config.getUseThreadPool()) {
					ExecutorService ex = SingletonThreadPoolExecutors.getThreadPool(
							ExecutorBlockingMode.NON_BLOCKING, Thread.currentThread().isDaemon());
					ex.submit(r);
				}
				else {
					Thread t = new Thread(r);
					t.setName("Message type specific processes");
					t.setDaemon(Thread.currentThread().isDaemon());
					t.start();
				}
			}
			catch (OutOfMemoryError e) {
				logger.log(Level.SEVERE, "# of threads: " + Thread.activeCount(), e);

//				Thread[] tarray = new Thread[Thread.activeCount()];
//				Thread.enumerate(tarray);
//				for (Thread t: tarray) System.out.println("Th: " + t.getName());
//				System.out.flush();

				throw e;
			}
		}

		// reports the routing result to the initiator
		List<Integer> notForwardedIndexList = new ArrayList<Integer>();

		for (int i = 0; i < target.length; i++) {
			if (!forwarded[i]) notForwardedIndexList.add(i);
		}

		if (!notForwardedIndexList.isEmpty()) {
			// get candidates for the root node
			ID[] partOfTarget = new ID[notForwardedIndexList.size()];
			RoutingResult[] partOfResult = new RoutingResult[notForwardedIndexList.size()];
			Serializable[] partOfCallbackResult = new Serializable[notForwardedIndexList.size()];

			for (int i = 0; i < notForwardedIndexList.size(); i++) {
				// target
				partOfTarget[i] = target[notForwardedIndexList.get(i)];

				// routing result
				IDAddressPair[] rootCands = algorithm.rootCandidates(partOfTarget[i], numRootCandidates);

				if ((oldMsgTag == Tag.REC_ROUTE_JOIN.getNumber()
						|| oldMsgTag == Tag.REC_TERMINATE_JOIN.getNumber())
						&& initiator.equals(rootCands[0])) {
					// remove initiator from the first place on the root candate list
					IDAddressPair[] orig = rootCands;
					rootCands = new IDAddressPair[rootCands.length - 1];
					System.arraycopy(orig, 1, rootCands, 0, rootCands.length);
				}

				partOfResult[i] = new RoutingResult(route, rootCands);

				// callback result
				partOfCallbackResult[i] = callbackResult[notForwardedIndexList.get(i)];
			}

			// this node is the destination, or failed to send
			Message repMsg = RoutingDriverMessageFactory.getRecResultMessage(
					this.getSelfIDAddressPair(),
					succeed, partOfTarget, partOfResult, partOfCallbackResult);
			RoutingDriverMessageFactory.setBlackList(repMsg, blackList);

			try {
				sender.send(initiator.getAddress(), repMsg);
//System.out.println("replied from " + getSelfIDAddressPair().getAddress() + " to " + initiator.getAddress()
//+ " for " + target[0].toString().substring(0, 4) + "..");

				for (int i: notForwardedIndexList) { 
					forwarded[i] = true;
				}
			}
			catch (IOException e) {
				// sending failure
				logger.log(Level.WARNING, "Failed to report to the initiator: " + initiator.getAddress()
						+ " on " + getSelfIDAddressPair().getAddress());

				super.fail(initiator);
			}
		}	// if (!notForwardedIndexList.isEmpty())

		boolean ret = true;
		for (boolean b: forwarded) ret &= b;

		return ret;
	}

	private final class Forwarder implements Callable<Boolean>, Runnable {
		private final boolean adjustLastHop;
		private final boolean terminate;
		private final int msgTag; private final IDAddressPair lastHop;
		private final List<IDAddressPair>[] closestNodes; private final RoutingContext[] routingContext;
		private final ID[] target; private final int numRootCandidates; private final IDAddressPair initiator; private int ttl;
		private final CallbackResultFilter filter; private final int callbackTag; private final Serializable[][] callbackArgs;
		private final RoutingHop[] route;
		private final IDAddressPair[] blackList;

		private boolean result;

		Forwarder(boolean adjustLastHop,
				boolean terminate,
				int msgTag, IDAddressPair lastHop,
				List<IDAddressPair>[] closestNodes, RoutingContext[] routingContext,
				ID[] target, int numRootCandidates, IDAddressPair initiator, int ttl,
				CallbackResultFilter filter, int callbackTag, Serializable[][] callbackArgs,
				RoutingHop[] route,
				IDAddressPair[] blackList) {
			this.adjustLastHop = adjustLastHop;
			this.terminate = terminate;
			this.msgTag = msgTag;
			this.lastHop = lastHop;
			this.closestNodes = closestNodes; this.routingContext = routingContext;
			this.target = target; this.numRootCandidates = numRootCandidates; this.initiator = initiator; this.ttl = ttl;
			this.filter = filter; this.callbackTag = callbackTag; this.callbackArgs = callbackArgs;
			this.route = route;
			this.blackList = blackList;
		}

		public void run() {
			try {
				this.call();
			}
			catch (Exception e) {
				logger.log(Level.SEVERE, "A Querier threw an exception.", e);
			}
		}

		public Boolean call() {
			result = forwardOrReturnResult(
					this.adjustLastHop,
					this.terminate,
					this.msgTag, this.lastHop,
					this.closestNodes, this.routingContext,
					this.target, this.numRootCandidates, this.initiator, this.ttl,
					this.filter, this.callbackTag, this.callbackArgs,
					this.route,
					this.blackList);

			return result;
		}

		public boolean getResult() { return this.result; }
	}
}
