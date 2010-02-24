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

package ow.routing.impl;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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
import ow.util.concurrent.SingletonThreadPoolExecutors;
import ow.util.concurrent.ExecutorBlockingMode;

/**
 * A routing driver which performs iterative forwarding/routing/lookup.
 *
 * @see ow.routing.RoutingServiceProvider#getService(RoutingServiceConfiguration, MessagingProvider, ID, int, long)
 * @see ow.routing.impl.RecursiveRoutingDriver
 */
public final class IterativeRoutingDriver extends AbstractRoutingDriver {
	protected IterativeRoutingDriver(RoutingServiceConfiguration conf,
			MessagingProvider provider, MessageReceiver receiver,
			RoutingAlgorithmProvider algoProvider, RoutingAlgorithmConfiguration algoConf,
			ID selfID)
				throws IOException {
		super(conf, provider, receiver, algoProvider, algoConf, selfID);

		// register message handlers
		prepareHandlers();
	}

	public RoutingResult[] routeToRootNode(ID[] target, int numRootCandidates) {
		return route(
				true & super.adjustLastHop,
				Tag.ITE_ROUTE_NONE,
				target, numRootCandidates,
				null, null, -1, null,
				null);
	}

	public RoutingResult[] routeToClosestNode(ID[] target, int numRootCandidates) {	// for RoutingRuntime interface
		return route(
				false,
				Tag.ITE_ROUTE_NONE,
				target, numRootCandidates,
				null, null, -1, null,
				null);
	}

	public RoutingResult[] invokeCallbacksOnRoute(ID[] target, int numRootCandidates,
			Serializable[][] returnedValue,
			CallbackResultFilter filter, int tag, Serializable[][] args) {
		return route(
				true & super.adjustLastHop,
				Tag.ITE_ROUTE_INVOKE,
				target, numRootCandidates,
				filter, returnedValue, tag, args,
				null);
	}

	public RoutingResult join(MessagingAddress initialContact)
			throws RoutingException{
		ID[] tgts = { this.getSelfIDAddressPair().getID() };

		RoutingResult[] res = route(
				true & super.adjustLastHop,
				Tag.ITE_ROUTE_JOIN,
				tgts, this.config.getNumOfRootCandidatesRequestedWhenJoining(),
				null, null, -1, null,
				initialContact);

		if (res == null || res[0] == null) throw new RoutingException();

		algorithm.join(res[0].getRootCandidates());
			// the algorithm instance performs the joining process 

		return res[0];
	}

	private RoutingResult[] route(
			boolean adjustLastHop,
			Tag msgType,
			ID[] target, int numRootCandidates,
			CallbackResultFilter filter, Serializable[][] resultingCallbackResult, int callbackTag, Serializable[][] callbackArgs,
			MessagingAddress joinInitialContact) {
//System.out.print("route:");
//System.out.println("  msg: " + Tag.getNameByNumber(msgType.getNumber()));
//System.out.println("  adj: " + adjustLastHop);
//for (ID t: target) System.out.print(" " + t.toString().substring(0, 4) + "..");
//System.out.println();
//System.out.flush();
		if (numRootCandidates < 1) numRootCandidates = 1;

		// calculate query concurrency
		int queryConcurrency = this.config.getQueryConcurrency();
		if ((!this.queryToAllContacts)
				|| (msgType == Tag.ITE_ROUTE_JOIN)
				|| (queryConcurrency <= 0)) {
			queryConcurrency = 1;
		}

		// initialize route
		List<RoutingHop>[] route = new List/*<RoutingHop>*/[target.length];
		for (int i = 0; i < target.length; i++) {
			route[i] = new ArrayList<RoutingHop>();
			if (queryConcurrency > 2) {
				route[i] = Collections.synchronizedList(route[i]);
			}

			route[i].add(RoutingHop.newInstance(getSelfIDAddressPair()));
		}
		Set<MessagingAddress> blackList =
			Collections.synchronizedSet(new HashSet<MessagingAddress>());

		// notify messaging visualizer
		if (msgType != Tag.ITE_ROUTE_JOIN) {
/*
			MessagingReporter msgReporter = receiver.getMessagingReporter();

			msgReporter.notifyStatCollectorOfEmphasizeNode(
					this.getSelfIDAddressPair(), this.getSelfIDAddressPair().getID());
			msgReporter.notifyStatCollectorOfMarkedID(
					this.getSelfIDAddressPair(), target, 0);
*/
		}

		// initialize contact list
		ContactList[] contactList = new ContactList[target.length];
		for (int i = 0; i < target.length; i++) {
			contactList[i] = (this.queryToAllContacts ?
				new SortedContactList(target[i], algorithm, config.getNumOfNodesMaintained()) :
				new InsertedOrderContactList());
		}

		// initial set of nodes
		if (msgType == Tag.ITE_ROUTE_JOIN) {
			contactList[0].add(IDAddressPair.getIDAddressPair(null, joinInitialContact));
		}
		else {
			for (int i = 0; i < target.length; i++) {
				RoutingContext initialRoutingContext = algorithm.initialRoutingContext(target[i]);

				IDAddressPair[] closestNodes =
					algorithm.closestTo(target[i], config.getNumOfClosestNodesRequested(), initialRoutingContext);

				for (IDAddressPair elem: closestNodes) {
					if (elem == null) continue;

					contactList[i].add(elem);
				}
			}
		}

		// refine contact list iteratively

		// query
		RoutingResultTable<IDAddressPair[]> rootCandidateTable =
			new RoutingResultTable<IDAddressPair[]>();
		RoutingResultTable<Serializable> callbackResultTable = null;
		if (msgType == Tag.ITE_ROUTE_INVOKE)
			callbackResultTable = new RoutingResultTable<Serializable>();

//		if (queryConcurrency <= 1) {
		if (true) {
			// serial query
			Querier querier = new Querier(target,
					numRootCandidates, config.getTTL(), msgType, adjustLastHop,
					route, contactList, null, blackList,
					joinInitialContact,
					callbackTag, callbackArgs, filter,
					rootCandidateTable, callbackResultTable);

			Future<Boolean> f = null;
			Thread t = null;
			boolean timeout = false;

			try {
				// invoke a Thread to timeout
				if (config.getUseThreadPool()) {
					ExecutorService ex = SingletonThreadPoolExecutors.getThreadPool(
							ExecutorBlockingMode.NON_BLOCKING, Thread.currentThread().isDaemon());
					f = ex.submit((Callable<Boolean>)querier);

					f.get(config.getRoutingTimeout(), TimeUnit.MILLISECONDS);
				}
				else {
					t = new Thread(querier);
					t.setName("Querier");
					t.setDaemon(Thread.currentThread().isDaemon());
					t.start();

					t.join(config.getRoutingTimeout());	// does not throw TimeoutException

					if (t.isAlive()) {
						// timed out
						timeout = true;
					}
				}
			}
			catch (ExecutionException e) {
				Throwable cause = e.getCause();
				logger.log(Level.WARNING, "A Querier threw an Exception.", (cause != null ? cause : e));
			}
			catch (InterruptedException e) {
				logger.log(Level.WARNING, "Querier#call() interrupted on " + getSelfIDAddressPair().getAddress());
			}
			catch (TimeoutException e) {	// timed out
				logger.log(Level.WARNING, "Routing timeout on " + getSelfIDAddressPair().getAddress());
				timeout = true;
			}
			catch (OutOfMemoryError e) {
				logger.log(Level.SEVERE, "# of threads: " + Thread.activeCount(), e);
				throw e;
			}

			if (timeout) {
				// interrupt a Querier
				if (f != null)
					f.cancel(true);
				else if (t != null)
					t.interrupt();

				rootCandidateTable.clear();
			}

			for (int i = 0; i < target.length; i++) {
				IDAddressPair contact = contactList[i].first();

				if (msgType == Tag.ITE_ROUTE_JOIN) {
					while (contact != null && getSelfIDAddressPair().equals(contact)) {
						// joining and destination is this node itself
						// it happens in case that this.queryToAllContacts is true.
						contactList[i].remove(contact);
						contact = contactList[i].first();
					}
				}
			}
		}
// concurrent queries disabled to implement collective routing (April 17, 2008)
//		else {
//			// concurrent queries
//			...
//		}	// if (queryConcurrency <= 1)

		// return
		RoutingResult[] ret = new RoutingResult[target.length];
		for (int i = 0; i < target.length; i++) {
			IDAddressPair goal;
			if (this.queryToAllContacts)
				goal = contactList[i].first();	// required for Kademlia
			else
				goal = route[i].get(route[i].size() - 1).getIDAddressPair().getIDAddressPair();
				// The last getIDAddressPair() derives an IDAddressPair from an IDAddressRoutingContextTriplet.

			// message dependent processes
			boolean isRootNode = getSelfIDAddressPair().equals(goal);

			if (msgType == Tag.ITE_ROUTE_INVOKE) {
				Serializable res =
					invokeCallbacks(target[i], callbackTag, callbackArgs[i], filter, null, isRootNode);

				callbackResultTable.put(target[i], getSelfIDAddressPair(), res);
			}
			else if (msgType == Tag.ITE_ROUTE_JOIN) {
				algorithm.join(this.getSelfIDAddressPair(), null, isRootNode);
			}

			// prepare RoutingResult and callback results
			IDAddressPair[] rootCandidates = rootCandidateTable.get(target[i], goal);
			if (rootCandidates != null) {
				RoutingHop[] routeArray = new RoutingHop[route[i].size()];
				route[i].toArray(routeArray);

				ret[i] = new RoutingResult(routeArray, rootCandidates);

				if (callbackResultTable != null
						&& resultingCallbackResult != null && resultingCallbackResult[i] != null) {
					resultingCallbackResult[i][0] = callbackResultTable.get(target[i], goal);
				}
			}
		}

		return ret;
	}

	private final class Querier implements Runnable, Callable<Boolean> {
		private ID[] target;
		private final int numRootCandidates;
		private int ttl;
		private final Tag msgType;
		private final boolean adjustLastHop;

		private final List<RoutingHop>[] route;
		private ContactList[] contactList;
		private IDAddressPair[] lastContact;
		private final Set<MessagingAddress> blackList;

		// for ITE_ROUTE_JOIN
		private final MessagingAddress joinInitialContact;

		// for ITE_ROUTE_INVOKE
		private final int callbackTag;
		private final Serializable[][] callbackArgs;
		private final CallbackResultFilter filter;

		// routing results tables
		private final RoutingResultTable<IDAddressPair[]> rootCandidateTable;
		private final RoutingResultTable<Serializable> callbackResultTable;

		Querier(ID[] target,
				int numRootCandidates,int ttl, Tag msgType, boolean adjustLastHop,
				List<RoutingHop>[] route, ContactList[] contactList, IDAddressPair[] lastContact, Set<MessagingAddress> blackList,
				MessagingAddress joinInitialContact,
				int callbackTag, Serializable[][] callbackArgs, CallbackResultFilter filter,
				RoutingResultTable<IDAddressPair[]> rootCandidateTable, RoutingResultTable<Serializable> callbackResultTable) {
			this.target = target;
			this.numRootCandidates = numRootCandidates;
			this.ttl = ttl;
			this.msgType = msgType;
			this.adjustLastHop = adjustLastHop;

			this.route = route;
			this.contactList = contactList;
			this.lastContact = lastContact;
			this.blackList = blackList;

			this.joinInitialContact = joinInitialContact;

			this.callbackTag = callbackTag;
			this.callbackArgs = callbackArgs;
			this.filter = filter;

			this.rootCandidateTable = rootCandidateTable;
			this.callbackResultTable = callbackResultTable;
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
//System.out.println("Querier#call on " + getSelfIDAddressPair().getAddress());
			IDAddressPair[] contacts = new IDAddressPair[this.target.length];
			IDAddressPair[] lastContacts = this.lastContact;
			int ttl = this.ttl;

			if (lastContacts == null) {
				lastContacts = new IDAddressPair[this.target.length];

				// here do not initialize contacts which assigined to lastContact soon
				//for (int i = 0; i < this.target.length; i++) {
				//	contacts[i] = getSelfIDAddressPair();
				//}
			}

			// iterative routing
			iterativeRouting:
			while (true) {
				IDAddressPair[][] closestNodes = null;

				if (ttlCheck(ttl, this.target, this.route[0])) break iterativeRouting;
				ttl--;

				// update last contacts
				for (int i = 0; i < target.length; i++) {
					lastContacts[i] = contacts[i];
				}

				// query
				Message replyMsg = null;
				do {	// while (replyMsg == null)
					// fork
					Set<IDAddressPair> contactSet = new HashSet<IDAddressPair>();
					boolean allContactsAreNull = true;
					boolean aContactIsNull = false;

					for (int i = 0; i < this.target.length; i++) {
						contacts[i] = (IterativeRoutingDriver.this.queryToAllContacts ?
								this.contactList[i].inspectExceptContactedNode() :
								this.contactList[i].inspect());

//StringBuilder sb = new StringBuilder();
//sb.append("tgt[").append(i).append("] ").append(this.target[i]).append("\n");
//sb.append("  contacts ").append((contacts[i] == null ? "(null)" : contacts[i].getAddress()));
						if (contacts[i] != null) {
							if (contacts[i].equals(lastContacts[i]))
								contacts[i] = null;
							else if (blackList.contains(contacts[i].getAddress())) {
								contactList[i].remove(contacts[i]);
								continue;
							}
						}

						if (contacts[i] != null) {
							contactSet.add(contacts[i].getIDAddressPair());
							allContactsAreNull = false;
						}
						else {
							contactSet.add(null);
							aContactIsNull = true;
						}
//if (contacts[i] == null) sb.append(" <- null cleared.");
//System.out.println(sb.toString());
//System.out.flush();

						// initialize lastContact
						if (lastContacts[i] == null) lastContacts[i] = getSelfIDAddressPair();
					}

					if (allContactsAreNull) break iterativeRouting;	// finish

					if (contactSet.size() > 1 || aContactIsNull) {	// fork
						Map<IDAddressPair,Querier> forkedQueriers =
							new HashMap<IDAddressPair,Querier>();

						List<Integer> contactIndexList = new ArrayList<Integer>();

						for (IDAddressPair c: contactSet) {
							contactIndexList.clear();

							for (int i = 0; i < this.target.length; i++) {
								if (c == null) {
									if (contacts[i] == null) {
										contactIndexList.add(i);
									}
								}
								else if (c.equals(contacts[i])) {
									contactIndexList.add(i);
								}
							}

							int nTgts = contactIndexList.size();
							ID[] forkedTarget = new ID[nTgts];
							List<RoutingHop>[] forkedRoute = new List/*<RoutingHop>*/[nTgts];
							ContactList[] forkedContactList = new ContactList[nTgts];
							IDAddressPair[] forkedLastContact = new IDAddressPair[nTgts];
							for (int i = 0; i < nTgts; i++) {
								int index = contactIndexList.get(i);
								forkedTarget[i] = target[index];
								forkedRoute[i] = route[index];
								forkedContactList[i] = contactList[index];
								forkedLastContact[i] = lastContacts[index];
							}
							Serializable[][] forkedCallbackArgs = null;
							if (callbackArgs != null) {
								forkedCallbackArgs = new Serializable[nTgts][];
								for (int i = 0; i < nTgts; i++) {
									int index = contactIndexList.get(i);
									forkedCallbackArgs[i] = callbackArgs[index];
								}
							}

							Querier q = new Querier(forkedTarget,
									this.numRootCandidates, this.ttl, this.msgType, this.adjustLastHop,
									forkedRoute, forkedContactList, forkedLastContact, this.blackList,
									this.joinInitialContact,
									this.callbackTag, forkedCallbackArgs, this.filter,
									this.rootCandidateTable, this.callbackResultTable);
							forkedQueriers.put(c, q);
						}

						// execute
						boolean querierResult = true;
						Set<Future<Boolean>> fSet = null;
						Set<Thread> tSet = null;

						try {
							if (config.getUseThreadPool()) {
								fSet = new HashSet<Future<Boolean>>();
								Querier firstQuerier = null;

								ExecutorService ex = SingletonThreadPoolExecutors.getThreadPool(
										ExecutorBlockingMode.REJECTING, Thread.currentThread().isDaemon());

								for (Querier q: forkedQueriers.values()) {
									if (firstQuerier == null) { firstQuerier = q; continue; }

									try {
										Future<Boolean> f = ex.submit((Callable<Boolean>)q);
										fSet.add(f);
									}
									catch (RejectedExecutionException e) {
										// invoke directly if rejected
										// Note that this is required to avoid deadlocks
										querierResult |= firstQuerier.call();
									}
								}

								querierResult |= firstQuerier.call();	// direct invocation (an optimization)

								for (Future<Boolean> f: fSet) {
									querierResult |= f.get();
								}
								// Note that this Querier can be blocked here
								// in case that the number of threads is limited.
								// Above ...submit()s consume a thread and this Querier own also keeps a thread.
								// If threads runs out, a submitted Querier cannot run and f.get() is blocked.
							}
							else {
								tSet = new HashSet<Thread>();
								Querier firstQuerier = null;

								for (Querier q: forkedQueriers.values()) {
									if (firstQuerier == null) { firstQuerier = q; continue; }

									Thread t = new Thread(q);
									t.setName("Querier");
									t.setDaemon(Thread.currentThread().isDaemon());
									tSet.add(t);
									t.start();
								}

								firstQuerier.call();	// direct invocation (an optimization)

								for (Thread t: tSet) t.join();
							}
						}
						catch (ExecutionException e) {
							Throwable cause = e.getCause();
							logger.log(Level.WARNING, "A Querier threw an Exception.", (cause != null ? cause : e));
							querierResult = false;
						}
						catch (InterruptedException e) {
							logger.log(Level.WARNING, "Querier#call() interrupted on " + getSelfIDAddressPair().getAddress());
							querierResult = false;

							// interrupt sub Queriers
							if (fSet != null)
								for (Future<Boolean> f: fSet) f.cancel(true);
							else if (tSet != null)
								for (Thread t: tSet) t.interrupt();
						}
						catch (OutOfMemoryError e) {
							logger.log(Level.SEVERE, "# of threads: " + Thread.activeCount(), e);
							throw e;
						}

						return querierResult;
					}	// if (contactSet.size() > 1)	// fork

					// update contact
					for (int i = 0; i < this.target.length; i++) {
						contacts[i] = (IterativeRoutingDriver.this.queryToAllContacts ?
								this.contactList[i].firstExceptContactedNode() :
								this.contactList[i].first());
					}

					// in case of join,
					// skip the next hop if it is the destination (this node itself)
					if (msgType == Tag.ITE_ROUTE_JOIN
							&& this.target[0].equals(contacts[0].getID())) {
						// remove
						contactList[0].remove(contacts[0]);

						// update next contact
						//lastContact = contact;		// does not update lastContact
						contacts[0] = (IterativeRoutingDriver.this.queryToAllContacts ?
								this.contactList[0].inspectExceptContactedNode() :
								this.contactList[0].inspect());
						if (lastContacts[0] != null && lastContacts[0].equals(contacts[0]))	// also compares routing context
							contacts[0] = null;
						if (contacts[0] == null)
							break iterativeRouting;

						continue;
					}

					// send a query and receive a reply
					replyMsg = null;
					try {
						RoutingContext[] cxt = new RoutingContext[this.target.length];
						try {
							for (int i = 0; i < this.target.length; i++)
								cxt[i] = ((IDAddressRoutingContextTriplet)contacts[i]).getRoutingContext();
						}
						catch (ClassCastException e) { cxt = null; }

						Message requestMsg = null;
						if (msgType == Tag.ITE_ROUTE_NONE) {
							requestMsg = RoutingDriverMessageFactory.getIteRouteNoneMessage(
									getSelfIDAddressPair(),
									this.target, config.getNumOfClosestNodesRequested(), numRootCandidates, cxt);
						}
						else if (msgType == Tag.ITE_ROUTE_INVOKE) {
							requestMsg = RoutingDriverMessageFactory.getIteRouteInvokeMessage(
									getSelfIDAddressPair(),
									this.target, config.getNumOfClosestNodesRequested(), numRootCandidates, cxt,
									this.filter, this.callbackTag, this.callbackArgs, lastContacts);
						}
						else {	// ITE_ROUTE_JOIN
							requestMsg = RoutingDriverMessageFactory.getIteRouteJoinMessage(
									getSelfIDAddressPair(),
									getSelfIDAddressPair(), config.getNumOfClosestNodesRequested(), numRootCandidates, cxt,
									lastContacts);
						}

						replyMsg = sender.sendAndReceive(contacts[0].getAddress(), requestMsg);
										// throws IOException

						// fill ID of contact
						for (int i = 0; i < target.length; i++){
							synchronized (contacts[i]) {
								if (contacts[i].getID() == null) {
									// this is the case in the first iteration of joining
									contacts[i].setID(replyMsg.getSource().getID());

									contactList[i].addAsContacted(contacts[i]);
								}
							}
						}

						// notify the routing algorithm
						algorithm.touch(replyMsg.getSource());
							// should be lazy (delayed and/or unified)?

						if (replyMsg.getTag() != Tag.ITE_REPLY.getNumber()) {
							logger.log(Level.SEVERE, "Received message is not ITE_REPLY: " + Tag.getNameByNumber(replyMsg.getTag()));

							replyMsg = null;
							break;
						}

						// parse the reply
						Serializable[] contents = replyMsg.getContents();
						closestNodes = (IDAddressPair[][])contents[0];
						IDAddressPair[][] rootCandidates = (IDAddressPair[][])contents[1];
						Serializable[] callbackResult = (Serializable[])contents[2];

						for (int i = 0; i < target.length; i++) {
							this.rootCandidateTable.put(target[i], replyMsg.getSource(), rootCandidates[i]);

							if (this.callbackResultTable != null)
								this.callbackResultTable.put(target[i], replyMsg.getSource(), callbackResult[i]);
						}

						break;
					}
					catch (IOException e) {
						logger.log(Level.WARNING, "Sending or receiving failed: "
								+ contacts[0].getAddress()
								+ " on " + getSelfIDAddressPair().getAddress());
					}

					// in case of failure
					if (contacts[0].getID() != null) { // In an initial join iteration, ID is null.
						IterativeRoutingDriver.super.fail(contacts[0]);
							// tell the algorithm of a failure of the best node

						this.blackList.add(contacts[0].getAddress());
					}

					for (int i = 0; i < target.length; i++) {
						synchronized (this.contactList[i]) {
							this.contactList[i].remove(contacts[i]);
						}
					}
				} while (replyMsg == null);	// query

				for (int i = 0; i < target.length; i++) {
					if (!(route[i].size() <= 1 && getSelfIDAddressPair().equals(contacts[0])))	// 1st hop is not the node itself
						this.route[i].add(RoutingHop.newInstance(contacts[0]));
				}

				// add the nodes in the reply to contact list
				if (msgType == Tag.ITE_ROUTE_JOIN &&
						closestNodes != null && closestNodes.length >= 1 &&
						closestNodes[0].equals(getSelfIDAddressPair())) {
					closestNodes[0] = null;
				}

				for (int i = 0; i < target.length; i++) {
					if (closestNodes[i] != null) {
						synchronized (this.contactList[i]) {
							if (!IterativeRoutingDriver.this.queryToAllContacts) {
								// refresh contact list
								this.contactList[i].clear();
							}

							for (IDAddressPair p: closestNodes[i]) {
								if (p == null) continue;
								if (!this.blackList.contains(p.getAddress())) {
									this.contactList[i].add(p);
								}
							}
						}
					}
				}
			}	// iterativeRouting: while

			// adjust last hop
			if (adjustLastHop) {
				List<IDAddressPair>[] adjustedLastHops = new LinkedList/*<IDAddressPair>*/[target.length];

				Set<IDAddressPair> contactSet = new HashSet<IDAddressPair>();
				List<Integer> indexList = new ArrayList<Integer>();

				retryAdjustment:
				while (true) {
					contactSet.clear();

					for (int i = 0; i < target.length; i++) {
						contacts[i] = contactList[i].first();

						if (contacts[i] != null) {
							contactList[i].remove(contacts[i]);	// remove from the original list
							contactSet.add(contacts[i].getIDAddressPair());
						}
						else {
							// failed to adjust for i
							this.rootCandidateTable.clear(this.target[i]);
						}
					}

					if (contactSet.size() <= 0 || contacts[0].getID() == null) {
						logger.log(Level.WARNING, "Adjustment failed because contact is null.");

						this.rootCandidateTable.clear();
						return false;
					}

					for (IDAddressPair contact: contactSet) {
						indexList.clear();

						for (int i = 0; i < target.length; i++) {
							if (contact.equals(contacts[i]))
								indexList.add(i);
						}

						ID[] forkedTarget = new ID[indexList.size()];
						for (int i = 0; i < indexList.size(); i++) {
							forkedTarget[i] = target[indexList.get(i)];
						}

						Message reqMsg = RoutingDriverMessageFactory.getIteAdjustLastHopReq(
								getSelfIDAddressPair(), forkedTarget);
						Message repMsg = null;
						try {
							repMsg = sender.sendAndReceive(contact.getAddress(), reqMsg);
						}
						catch (IOException e) {
							logger.log(Level.WARNING, "Failed to send/receive ITE_ADJUST_LAST_HOP_REQ: "
									+ contact.getAddress()
									+ " on " + getSelfIDAddressPair().getAddress(), e);

							continue retryAdjustment;
						}

						Serializable[] contents = repMsg.getContents();
						IDAddressPair[][] forkedAdjLastHops = (IDAddressPair[][])contents[0];

						if (forkedAdjLastHops != null) {
							for (int i = 0; i < indexList.size(); i++) {
								int index = indexList.get(i);
								adjustedLastHops[index] = new LinkedList<IDAddressPair>();
//StringBuilder sb = new StringBuilder();
//sb.append("adjusted for " + target[i].toString().substring(0, 6)).append(":");
								for (IDAddressPair p: forkedAdjLastHops[i]) {
									if (p != null) {
										adjustedLastHops[index].add(p);
//sb.append(" ").append(p.getAddress());
									}
								}
//System.out.println(sb.toString());
//System.out.flush();
							}
						}

						// clear contact lists corresponding to the succeeded contact
						for (int i: indexList) {
							contactList[i].clear();
						}
					}	// for (IDAddressPair contact: contactSet)

					break;
				}	// while (true)

//				for (int i = 0; i < target.length; i++) {
//					if (adjustedLastHops[i] == null) {
//						rootCandidates[i] = null;
//						callbackResult[i] = null;
//					}
//				}

				lastContacts = contacts;

				// send terminating message
				//int ttl = config.getTTL() - route[0].size() + 1;

				retryTermination:
				while (true) {
					contacts = new IDAddressPair[target.length];
					contactSet.clear();

					for (int i = 0; i < target.length; i++) {
						if (adjustedLastHops[i] != null) {
							while (true) {
								try {
									contacts[i] = adjustedLastHops[i].get(0);
								}
								catch (IndexOutOfBoundsException e) { break; }

								if (contacts[i] != null
									&& (blackList.contains(contacts[i].getAddress())
										|| (msgType == Tag.ITE_ROUTE_JOIN && getSelfIDAddressPair().equals(contacts[i])))) {
									// try next contact
									adjustedLastHops[i].remove(0);
								}
								else {
									break;
								}
							}

							if (contacts[i] != null) {
								contactSet.add(contacts[i].getIDAddressPair());
							}
						}
					}

					if (contactSet.isEmpty()) break;

//System.out.print("contactSet:");
//for (IDAddressPair p: contactSet) { System.out.print(" " + p.getAddress()); }
//System.out.println();
//System.out.flush();
					for (IDAddressPair contact: contactSet) {
						indexList.clear();

						for (int i = 0; i < target.length; i++) {
							if (contact.equals(contacts[i])) {
								indexList.add(i);
							}
						}

						ID[] forkedTarget = new ID[indexList.size()];
						IDAddressPair[] forkedLastContacts = new IDAddressPair[indexList.size()];
						for (int i = 0; i < indexList.size(); i++) {
							forkedTarget[i] = target[indexList.get(i)];
							forkedLastContacts[i] = lastContacts[indexList.get(i)];
						}
						Serializable[][] forkedCallbackArgs = null;
						if (callbackArgs != null) {
							forkedCallbackArgs = new Serializable[indexList.size()][];
							for (int i = 0; i < indexList.size(); i++) {
								forkedCallbackArgs[i] = callbackArgs[indexList.get(i)];
							}
						}

						// prepare a Message
						Message requestMsg;
						if (msgType == Tag.ITE_ROUTE_NONE) {
							requestMsg = RoutingDriverMessageFactory.getIteTerminateNoneMessage(
									getSelfIDAddressPair(), forkedTarget, numRootCandidates);
						}
						else if (msgType == Tag.ITE_ROUTE_INVOKE) {
							requestMsg = RoutingDriverMessageFactory.getIteTerminateInvokeMessage(
									getSelfIDAddressPair(), forkedTarget, numRootCandidates,
									filter, callbackTag, forkedCallbackArgs, forkedLastContacts);
						}
						else {	// ITE_ROUTE_JOIN
							requestMsg = RoutingDriverMessageFactory.getIteTerminateJoinMessage(
									getSelfIDAddressPair(),
									getSelfIDAddressPair(), numRootCandidates, forkedLastContacts);
						}

						// send
						Message replyMsg = null;

						try {
//System.out.println("terminate msg to " + contact.getAddress());
							replyMsg = sender.sendAndReceive(contact.getAddress(), requestMsg);

							// notify the routing algorithm
							algorithm.touch(replyMsg.getSource());
								// should be lazy (delayed and/or unified)?
						}
						catch (IOException e) {
							logger.log(Level.WARNING, "Sending or receiving failed: "
									+ contact.getAddress()
									+ " on " + getSelfIDAddressPair().getAddress());

							for (int index: indexList) {
								adjustedLastHops[index].remove(contact);
							}

							blackList.add(contact.getAddress());

							// notify the routing algorithm
							IterativeRoutingDriver.super.fail(contact);

							continue retryTermination;
						}

						if (replyMsg.getTag() != Tag.ITE_REPLY.getNumber()) {
							logger.log(Level.SEVERE, "Received message is not ITE_REPLY: " + Tag.getNameByNumber(replyMsg.getTag()));

							replyMsg = null;
							continue;
						}

						for (int i = 0; i < indexList.size(); i++) {
							List<RoutingHop> r = route[indexList.get(i)];
							if (!r.get(r.size() - 1).getIDAddressPair().equals(contact))	// the last hop is not contact
								r.add(RoutingHop.newInstance(contact));
						}

						// parse the reply
						Serializable[] contents = replyMsg.getContents();
						IDAddressPair[][] forkedRootCandidates = (IDAddressPair[][])contents[1];
						Serializable[] callbackResult = (Serializable[])contents[2];

						// check if contact is the responsible node
						boolean retry = false;
						for (int i = 0; i < forkedTarget.length; i++) {
							if (forkedRootCandidates[i] != null) {
								IDAddressPair root = null;
								for (IDAddressPair cand: forkedRootCandidates[i]) {
									if (cand != null && !blackList.contains(cand.getAddress())) {
										root = cand;
										break;
									}
								}

								if (!contact.equals(root)) {
									logger.log(Level.WARNING/*INFO*/,
											"Target of the terminating msg is not responsible for "
											+ forkedTarget[i].toString(-1) + "... adjusted from "
											+ contact.getAddress() + " (" + contact.getID().toString(-1) + ") to "
											+ root.getAddress() + " (" + root.getID().toString(-1) + ").");

									adjustedLastHops[indexList.get(i)].remove(contact);
									adjustedLastHops[indexList.get(i)].add(0, root);

									retry = true;
									continue;
								}
							}

							this.rootCandidateTable.put(forkedTarget[i], replyMsg.getSource(), forkedRootCandidates[i]);

							if (callbackResult != null) {
								if (this.callbackResultTable != null)
									this.callbackResultTable.put(forkedTarget[i], replyMsg.getSource(), callbackResult[i]);

//System.out.println("Callback result: " + callbackResult[i]);
								logger.log(Level.INFO, "Callback result: " + callbackResult[i]);
							}

							// clear to avoid retry
							adjustedLastHops[indexList.get(i)] = null;
						}
						if (retry) {
							ttl--;
							if (ttlCheck(ttl, forkedTarget, route[indexList.get(0)])) {
								for (int i = 0; i < forkedTarget.length; i++) {
									adjustedLastHops[indexList.get(i)] = null;
								}

								break;
							}

							continue retryTermination;
						}
					}	// for (IDAddressPair contact: contactSet)

					break;
				}	// retryTermination: while (true)
			}	// if (adjustLastHop)

			return true;
		}
	}

	private boolean ttlCheck(int ttl, ID[] target, List<RoutingHop> route) {
		if (ttl <= 0) {
			StringBuilder sb = new StringBuilder();
			sb.append("TTL expired (target");
			for (ID t: target) { sb.append(" ").append(t.toString().substring(0, 6)); }
			sb.append("):");
			for (RoutingHop n: route) {
				sb.append(" ");
				sb.append(n.getIDAddressPair().getAddress().getHostname());
			}

			logger.log(Level.WARNING, sb.toString(), new Throwable());

			return true;
		}

		return false;
	}

	private final static class RoutingResultTable<V> {
		private final class Entry {
			private final ID target; private final IDAddressPair node;
			Entry(ID target, IDAddressPair node) { this.target = target; this.node = node.getIDAddressPair(); }
			public int hashCode() { return this.target.hashCode() ^ this.node.hashCode(); }
			public boolean equals(Object o) {
				Entry ent;
				try { ent = (Entry)o; } catch (ClassCastException e) { return false; }
				return this.target.equals(ent.target) && this.node.equals(ent.node);
			} 
		}

		private Map<Entry,V> table = new HashMap<Entry,V>();

		public synchronized void put(ID target, IDAddressPair node, V v) {
			this.table.put(new Entry(target, node), v);
		}

		public synchronized V get(ID target, IDAddressPair node) {
			return this.table.get(new Entry(target, node));
		}

		public synchronized void clear(ID target) {
			Set<Entry> keySet = this.table.keySet();
			Entry[] keyArray = new RoutingResultTable/*<V>*/.Entry[keySet.size()];
			this.table.keySet().toArray(keyArray);

			for (Entry e: keyArray) {
				if (target.equals(e.target)) {
					this.table.remove(e);
				}
			}
		}

		public synchronized void clear() { this.table.clear(); }
	}

	/**
	 * Prepare message handlers for received messages.
	 */
	private void prepareHandlers() {
		MessageHandler handler;

		// ITE_{,ROUTE_}{NONE,INVOKE,JOIN}
		handler = new MessageHandler() {
			public Message process(final Message msg) {
				// parse the Message
				final ID[] target;
				int numClosestNodes = 1, numRootCandidates = 1;
				RoutingContext[] routingContext = null;
				CallbackResultFilter filter = null;
				int tag = -1;
				Serializable[][] args = null;
				IDAddressPair[] lastHop = null;
				IDAddressPair joiningNode = null;

				boolean routing;

				Serializable[] contents = msg.getContents();
				if (msg.getTag() == Tag.ITE_ROUTE_NONE.getNumber()) {
					target = (ID[])contents[0];
					numClosestNodes = (Integer)contents[1];
					numRootCandidates = (Integer)contents[2];
					routingContext = (RoutingContext[])contents[3];

					routing = true;
				}
				else if (msg.getTag() == Tag.ITE_ROUTE_INVOKE.getNumber()) {
					target = (ID[])contents[0];
					numClosestNodes = (Integer)contents[1];
					numRootCandidates = (Integer)contents[2];
					routingContext = (RoutingContext[])contents[3];

					filter = (CallbackResultFilter)contents[4];
					tag = (Integer)contents[5];
					args = (Serializable[][])contents[6];
					lastHop = (IDAddressPair[])contents[7];

					routing = true;
				}
				else if (msg.getTag() == Tag.ITE_ROUTE_JOIN.getNumber()) {
					joiningNode = (IDAddressPair)contents[0];
					target = new ID[1]; target[0] = joiningNode.getID();
					numClosestNodes = (Integer)contents[1];
					numRootCandidates = (Integer)contents[2];
					routingContext = (RoutingContext[])contents[3];

					lastHop = (IDAddressPair[])contents[4];

					routing = true;
				}
				else if (msg.getTag() == Tag.ITE_TERMINATE_NONE.getNumber()) {
					target = (ID[])contents[0];
					numRootCandidates = (Integer)contents[1];

					routing = false;	// this node is the responsible node
				}
				else if (msg.getTag() == Tag.ITE_TERMINATE_INVOKE.getNumber()) {
					target = (ID[])contents[0];
					numRootCandidates = (Integer)contents[1];
					filter = (CallbackResultFilter)contents[2];
					tag = (Integer)contents[3];
					args = (Serializable[][])contents[4];
					lastHop = (IDAddressPair[])contents[5];

					routing = false;	// this node is the responsible node
				}
				else {	// ITE_TERMINATE_JOIN
					joiningNode = (IDAddressPair)contents[0]; 
					target = new ID[1]; target[0] = joiningNode.getID();
					numRootCandidates = (Integer)contents[1];
					lastHop = (IDAddressPair[])contents[2];

					routing = false;	// this node is the responsible node
				}

				// routing
				boolean[] isRootNode = new boolean[target.length];

				IDAddressPair[][] closestNodes = new IDAddressPair[target.length][];
				IDAddressPair[][] adjustedLastHops = new IDAddressPair[target.length][];
				if (routingContext == null) routingContext = new RoutingContext[target.length];

				for (int i = 0; i < target.length; i++) {
					if (routing) {
						// calculate routing context if null
						if (routingContext[i] == null) {
							routingContext[i] = algorithm.initialRoutingContext(target[i]);
						}

						closestNodes[i] = algorithm.closestTo(target[i], numClosestNodes, routingContext[i]);

						isRootNode[i] =
							closestNodes[i][0] != null && closestNodes[i][0].equals(getSelfIDAddressPair());
/*
System.out.println("isRootNode: " + isRootNode[i]);
System.out.println("  self   : " + getSelfIDAddressPair());
for (int j = 0; j < closestNodes[i].length; j++) {
	if (closestNodes[i][j] != null)
		System.out.println("  closest[" + j + "]: " + closestNodes[i][i]);
}
*/
						// adjust last hop
						if (isRootNode[i]) {
							adjustedLastHops[i] = algorithm.adjustRoot(target[i]);
/*
if (adjustedLastHops[i] != null) {
System.out.print("  adjustedLastHops:");
for (IDAddressPair p: adjustedLastHops[i]) System.out.print(" " + p);
System.out.println();
}
*/
							if (adjustedLastHops[i] != null) {
								isRootNode[i] = adjustedLastHops[i][0].equals(getSelfIDAddressPair());
							}
						}
					}	// if (routing)
					else {
						isRootNode[i] = true;
					}
				}	// for (int i = 0; i < target.length; i++)

				// notify the routing algorithm
				algorithm.touch(msg.getSource());
				if (lastHop != null) {
					for (IDAddressPair p: lastHop) {
						if (p == null || p.equals(getSelfIDAddressPair())) continue;
						algorithm.touch(p);
					}
				}

				// message type specific process
				Serializable[] callbackResult = new Serializable[target.length];
				if (msg.getTag() == Tag.ITE_ROUTE_INVOKE.getNumber()
						|| msg.getTag() == Tag.ITE_TERMINATE_INVOKE.getNumber()) {
					// invoke callbacks
					for (int i = 0; i < target.length; i++) {
						callbackResult[i] = invokeCallbacks(target[i], tag, args[i], filter, lastHop[i], isRootNode[i]);
						if (callbackResult[i] != null) {
							logger.log(Level.INFO, "A callback returned non-null object: " + callbackResult);
						}
					}
				}
				else if (msg.getTag() == Tag.ITE_ROUTE_JOIN.getNumber()
						|| msg.getTag() == Tag.ITE_TERMINATE_JOIN.getNumber()) {
					final IDAddressPair copiedJoiningNode = joiningNode;
					final IDAddressPair[] copiedLastHop = new IDAddressPair[lastHop.length];
					System.arraycopy(lastHop, 0, copiedLastHop, 0, lastHop.length);
					final boolean[] copiedIsRootNode = isRootNode;
					Runnable r = new Runnable() {
						public void run() {
							for (int i = 0; i < copiedLastHop.length; i++) {
								algorithm.join(copiedJoiningNode, copiedLastHop[i], copiedIsRootNode[i]);
							}
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

//						Thread[] tarray = new Thread[Thread.activeCount()];
//						Thread.enumerate(tarray);
//						for (Thread t: tarray) System.out.println("Th: " + t.getName());
//						System.out.flush();

						throw e;
					}
				}

				// get root candidates
				IDAddressPair[][] rootCandidates = new IDAddressPair[target.length][];
				for (int i = 0; i < target.length; i++)
					rootCandidates[i] = algorithm.rootCandidates(target[i], numRootCandidates);

				return RoutingDriverMessageFactory.getIteReplyMessage(getSelfIDAddressPair(), closestNodes, rootCandidates, callbackResult);
			}
		};
		addMessageHandler(Tag.ITE_ROUTE_NONE.getNumber(), handler);
		addMessageHandler(Tag.ITE_ROUTE_INVOKE.getNumber(), handler);
		addMessageHandler(Tag.ITE_ROUTE_JOIN.getNumber(), handler);
		addMessageHandler(Tag.ITE_TERMINATE_NONE.getNumber(), handler);
		addMessageHandler(Tag.ITE_TERMINATE_INVOKE.getNumber(), handler);
		addMessageHandler(Tag.ITE_TERMINATE_JOIN.getNumber(), handler);

		// ITE_ADJUST_LAST_HOP_REQ
		handler = new MessageHandler() {
			public Message process(Message msg) {
				Serializable[] contents = msg.getContents();
				ID[] target = (ID[])contents[0];

				// ask algorithm instance
				IDAddressPair[][] adjustedLastHops = new IDAddressPair[target.length][];
				for (int i = 0; i < target.length; i++) {
					adjustedLastHops[i] = algorithm.adjustRoot(target[i]);
				}

				Message repMsg = RoutingDriverMessageFactory.getIteAdjustLastHopRep(
						getSelfIDAddressPair(),
						adjustedLastHops);
				return repMsg;
			}
		};
		addMessageHandler(Tag.ITE_ADJUST_LAST_HOP_REQ.getNumber(), handler);
	}
}
