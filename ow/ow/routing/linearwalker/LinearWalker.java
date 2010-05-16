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

package ow.routing.linearwalker;

import java.io.IOException;
import java.io.Serializable;
import java.math.BigInteger;
import java.security.InvalidAlgorithmParameterException;
import java.util.Comparator;
import java.util.logging.Level;

import ow.id.ID;
import ow.id.IDAddressPair;
import ow.id.comparator.AlgoBasedFromSrcIDAddrPairComparator;
import ow.id.comparator.AlgoBasedTowardTargetIDAddrComparator;
import ow.messaging.Message;
import ow.messaging.MessageHandler;
import ow.messaging.Tag;
import ow.routing.RoutingAlgorithm;
import ow.routing.RoutingAlgorithmConfiguration;
import ow.routing.RoutingContext;
import ow.routing.RoutingService;
import ow.routing.impl.AbstractRoutingAlgorithm;
import ow.util.HTMLUtil;
import ow.util.Timer;

/**
 * An implementation of Consistent Hashing which tracks nodes linearly.
 * This is the base of Chord implementations, but works independently from Chord.
 */
public class LinearWalker extends AbstractRoutingAlgorithm {
	// messages
	private final Message reqSuccessorMessage;

	protected Comparator<IDAddressPair> towardSelfComparator;
	private Comparator<IDAddressPair> fromSelfComparator;

	protected LinearWalkerConfiguration config;
	protected final BigInteger sizeOfIdSpace;

	protected final RoutingAlgorithm algorithm;

	protected boolean stopped = false;
	protected boolean suspended = true;	// a created node is suspended.

	// routing table
	protected final int idSizeInBit;
	protected final SuccessorList successorList;
	protected IDAddressPair predecessor;

	// daemons
	private Stabilizer stabilizer;
	private Thread stabilizerThread = null;

	protected LinearWalker(RoutingAlgorithmConfiguration config, RoutingService routingSvc)
			throws InvalidAlgorithmParameterException {
		super(config, routingSvc);

		try {
			this.config = (LinearWalkerConfiguration)config;
		}
		catch (ClassCastException e) {
			throw new InvalidAlgorithmParameterException("The given config is not ConsistentHashingConfiguration.");
		}

		this.idSizeInBit = config.getIDSizeInByte() * 8;

		// prepare constants
		this.sizeOfIdSpace = BigInteger.ONE.shiftLeft(this.idSizeInBit);

		this.algorithm = this;	// points to this instance itself

		this.towardSelfComparator =
			new AlgoBasedTowardTargetIDAddrComparator(this, selfIDAddress.getID());
		this.fromSelfComparator =
			new AlgoBasedFromSrcIDAddrPairComparator(this, selfIDAddress.getID());

		// initialize routing table
		this.successorList = new SuccessorList(
				this, selfIDAddress, this.config.getSuccessorListLength());
		this.predecessor = selfIDAddress;

		// prepare messages
		this.reqSuccessorMessage =
			LinearWalkerMessageFactory.getReqSuccessorMessage(selfIDAddress);

		// initialize message handlers
		prepareHandlers();

		// does not invoke a stabilizer
		//this.startStabilizer();
	}

	private synchronized void startStabilizer() {
		if (config.getAggressiveJoiningMode()) return;

		if (this.stabilizer != null) return;	// to avoid multiple invocations

		this.stabilizer = new Stabilizer();

		if (config.getUseTimerInsteadOfThread()) {
			timer.schedule(this.stabilizer, Timer.currentTimeMillis(),
					true /*isDaemon*/, true /*executeConcurrently*/);
		}
		else if (this.stabilizerThread == null){
			this.stabilizerThread = new Thread(this.stabilizer);
			this.stabilizerThread.setName("Stabilizer on " + selfIDAddress.getAddress());
			this.stabilizerThread.setDaemon(true);
			this.stabilizerThread.start();
		}
	}

	private synchronized void stopStabilizer() {
		if (this.stabilizerThread != null) {
			this.stabilizerThread.interrupt();
			this.stabilizerThread = null;
		}
	}

	public synchronized void reset() {
		this.successorList.clear();
		this.predecessor = selfIDAddress;
	}

	public synchronized void stop() {
		this.stopped = true;
		this.stopStabilizer();

		// TODO: transfer indices
	}

	public synchronized void suspend() {
		this.suspended = true;
		this.stopStabilizer();
	}

	public synchronized void resume() {
		this.suspended = false;
		this.startStabilizer();
	}

	public BigInteger distance(ID to, ID from) {
		BigInteger toInt = to.toBigInteger();
		BigInteger fromInt = from.toBigInteger();

		BigInteger distance = toInt.subtract(fromInt);	// distance = to - from
		if (distance.compareTo(BigInteger.ZERO) <= 0) {
			distance = distance.add(this.sizeOfIdSpace);
				// distance = 2 ^ # of bit if to and from are the same ID
		}

		return distance;	// 1 <= distance <= 2 ^ # of bit
	}

	public void join(IDAddressPair[] neighbors /* are to be successor list */) {
		if (config.getAggressiveJoiningMode()) {
			boolean succeed = false;

			for (IDAddressPair succ: neighbors) {
				try {
					this.connectToSuccessorList(succ);
					succeed = true;
					break;
				} catch (IOException e) {}
			}
		}
		else {
			successorList.addAll(neighbors);
/*
System.out.println("LinearWalker#join on " + selfIDAddress.getAddress());
for (IDAddressPair n: neighbors) {
	System.out.println("  " + n);
}
*/
		}

		this.resume();
	}

	public IDAddressPair[] closestTo(ID target, int maxNum, RoutingContext cxt) {	// find_predecessor()
//System.out.print("On " + selfIDAddress.getAddress() + ", closestNodes(): " + target);
		return this.successorList.closestNodes(target, true);
				// successor list cut at target
	}

	public IDAddressPair[] rootCandidates(ID target, int maxNum) {
		return this.successorList.rootCandidates(target, maxNum, this.predecessor);
	}

	public IDAddressPair[] adjustRoot(ID rootCandidate) {
		return this.successorList.toArray();
	}

	public void touch(IDAddressPair from) {
		if (this.config.getUpdateRoutingTableByAllCommunications()) {
			this.successorList.add(from);
		}
	}

	public void forget(IDAddressPair failedNode) {
		// successor list
		this.successorList.remove(failedNode);

		// predecessor
		synchronized (this) {
			if (this.predecessor != null && this.predecessor.equals(failedNode)) {
				this.predecessor = selfIDAddress;
			}
		}
	}

	public boolean toReplace(IDAddressPair existingEntry, IDAddressPair newEntry) {
		// distance from this node is smaller -> better
		if (this.fromSelfComparator.compare(existingEntry, newEntry) <= 0)
			return false;
		else
			return true;
	}

	public void join(IDAddressPair joiningNode, IDAddressPair lastHop, boolean isFinalNode) {
		ID joiningNodeID = (joiningNode != null ? joiningNode.getID() : null);

		if (!this.selfIDAddress.getID().equals(joiningNodeID)) {
			// joining node is not this node itself
			this.resume();
		}
	}

	public String getRoutingTableString(int verboseLevel) {
		StringBuilder sb = new StringBuilder();

		sb.append("predecessor:\n ");
		sb.append(this.predecessor.toString(verboseLevel));
		sb.append("\n");

		sb.append("successor list: [");
		IDAddressPair[] slArray = this.successorList.toArray();
		for (IDAddressPair entry: slArray) {
			sb.append("\n ");
			sb.append(entry.toString(verboseLevel));
		}
		sb.append("\n]");

		return sb.toString();
	}

	public String getRoutingTableHTMLString() {
		StringBuilder sb = new StringBuilder();
		String url;

		sb.append("<h4>Predecessor</h4>\n");
		sb.append("<table>\n");
		if (this.predecessor != null) {
			url = HTMLUtil.convertMessagingAddressToURL(this.predecessor.getAddress());
			sb.append("<tr><td><a href=\"" + url + "\">" + HTMLUtil.stringInHTML(url) + "</a></td><td>"
					+ HTMLUtil.stringInHTML(this.predecessor.getID().toString()) + "</td></tr>\n");
		}
		else {
			sb.append("<tr><td>null</td></tr>\n");
		}
		sb.append("</table>\n");

		sb.append("<h4>Successor List</h4>\n");
		IDAddressPair[] slArray = this.successorList.toArray();
		sb.append("<table>\n");
		for (IDAddressPair entry: slArray) {
			url = HTMLUtil.convertMessagingAddressToURL(entry.getAddress());
			sb.append("<tr><td><a href=\"" + url + "\">" + HTMLUtil.stringInHTML(url) + "</a></td><td>"
					+ HTMLUtil.stringInHTML(entry.getID().toString()) + "</td></tr>\n");
		}
		sb.append("</table>\n");

		return sb.toString();
	}

	protected IDAddressPair connectToSuccessor(
			IDAddressPair successor,
			boolean forceSuccToSetPred)	// if true, forces successor to accept this node as the predecessor
				throws IOException {
		if (successor == null || successor.getID().equals(selfIDAddress.getID()))
			return null;

		Message reqMsg = LinearWalkerMessageFactory.getReqConnectMessage(selfIDAddress, forceSuccToSetPred);
		Message repMsg = null;

		try {
			repMsg = sender.sendAndReceive(successor.getAddress(), reqMsg);
		}
		catch (IOException e) {
			logger.log(Level.WARNING, "Failed to send a REQ_CONNECT msg or receive a REP_CONNECT msg.", e);
			this.fail(successor);
			throw e;
		}

		if (repMsg.getTag() != Tag.REP_CONNECT.getNumber()) {
			String receivedMsgName = Tag.getNameByNumber(repMsg.getTag());
			logger.log(Level.WARNING, "Expected message is REP_CONNECT: "
					+ receivedMsgName);

			throw new IOException("Invalid message received: " + receivedMsgName);
		}

		// parse the reply
		Serializable[] contents = repMsg.getContents();
		IDAddressPair predCandidate = (IDAddressPair)contents[0];
		IDAddressPair[] successors = (IDAddressPair[])contents[1];

		this.successorList.addAll(successors);
		this.algorithm.touch(successor);
			// notify the algorithm
			// does this.successorList.add(successor);

		return predCandidate;
	}

	private boolean connectToSuccessorList(IDAddressPair successor) throws IOException {
		final boolean forceSuccToSetPred = (successor != null);
		boolean successorChanged = false;
		IDAddressPair lastSucc = this.successorList.first();
			// The successor argument is possibly not the current successor (successorList.first()).

		if (successor == null) successor = lastSucc;

//System.out.print("connToSucc on " + selfIDAddress.getAddress().getHostname());
//if (successor != null) System.out.println(" to: " + successor.getAddress().getHostname()); else System.out.println();
		while (true) {
			if (successor == null || successor.equals(selfIDAddress)) break;

//System.out.print("  " + lastSucc.getAddress().getHostname());
//System.out.flush();
			IDAddressPair predOfSucc = this.connectToSuccessor(successor, forceSuccToSetPred);

			if (predOfSucc != null) {
				// update predecessor
				// not prescribed in the Chord paper
				synchronized (this) {
					if (towardSelfComparator.compare(predOfSucc, this.predecessor) < 0) {
						this.predecessor = predOfSucc;
					}
				}

				// update successor
				this.successorList.add(predOfSucc);	// anyway, try adding
			}

			successor = this.successorList.first();
//System.out.println(" -> " + successor.getAddress().getHostname());
//System.out.flush();
			if (successor.equals(lastSucc))	break;
				// successor has not changed
			lastSucc = successor;

			successorChanged = true;
		}

		return successorChanged;
	}

	// confirm whether predecessor is alive or not, and update predecessor
	private void updatePredecessor() {
		IDAddressPair pred = this.predecessor;
		if (pred == null || selfIDAddress.equals(pred)) return;

		Message reqMsg = this.reqSuccessorMessage;
		Message repMsg = null;
		try {
			repMsg = sender.sendAndReceive(pred.getAddress(), reqMsg);
		}
		catch (IOException e) {
			logger.log(Level.WARNING, "Failed to send a REQ_SUCCESSOR msg or receive a REP_SUCCESSOR msg.", e);
			this.fail(pred);
			return;
		}

		Serializable[] contents = repMsg.getContents();
		IDAddressPair succOfPred = (IDAddressPair)contents[0];

		synchronized (this) {
			if (towardSelfComparator.compare(succOfPred, this.predecessor) < 0) {
				this.predecessor = succOfPred;
			}
		}
	}

	protected void prepareHandlers() {
		this.prepareHandlers(false);
	}

	protected void prepareHandlers(boolean ignoreReqConnectMessage) {
		MessageHandler handler;

		// REQ_CONNECT
		// first half of init_finger_table(n')
		if (!ignoreReqConnectMessage) {
			handler = new ReqConnectMessageHandler();
			this.runtime.addMessageHandler(Tag.REQ_CONNECT.getNumber(), handler);
		}

		// REQ_SUCCESSOR
		handler = new MessageHandler() {
			public Message process(Message msg) {
				return LinearWalkerMessageFactory.getRepSuccessorMessage(selfIDAddress,
						successorList.first());
			}
		};
		this.runtime.addMessageHandler(Tag.REQ_SUCCESSOR.getNumber(), handler);
	}

	// MessageHandler for REQ_CONNECT
	// registered by superclasses
	public class ReqConnectMessageHandler implements MessageHandler {
		public Message process(Message msg) {
			algorithm.touch(msg.getSource());	// notify the algorithm

			Serializable[] contents = msg.getContents();
			boolean forceSuccToSetPred = (Boolean)contents[0];
				// if true, accepts the contacting node as the predecessor

			IDAddressPair lastPredecessor = predecessor;

			// update predecessor
			IDAddressPair predCandidate = msg.getSource();
			synchronized (LinearWalker.this) {
				if (!config.getAggressiveJoiningMode()) {
					// check the received predecessor
					if (forceSuccToSetPred
							|| predCandidate.getID().equals(predecessor.getID())	// just perf optimization
							|| towardSelfComparator.compare(predCandidate, predecessor) < 0) {
						predecessor = predCandidate;
					}
				}
				else {
					predecessor = predCandidate;
				}
			}

			// reply
			IDAddressPair[] successorArray = successorList.toArray();
			Message repMsg = LinearWalkerMessageFactory.getRepConnectMessage(selfIDAddress,
					lastPredecessor, successorArray);

			// try to add the predecessor to finger table and successor list
			// these does not exist in the Figure 6
			successorList.add(predCandidate);

			return repMsg;
		}
	}

	private final class Stabilizer implements Runnable {
		private long interval = config.getStabilizeMinInterval();
		private int updatePredecessorFreq = config.getUpdatePredecessorFreq();

		public void run() {
			boolean successorChanged = false;

			try {
				while (true) {
					synchronized (LinearWalker.this) {
						if (stopped || suspended) {
							LinearWalker.this.stabilizer = null;
							LinearWalker.this.stabilizerThread = null;
							break;
						}
					}

					// contact to successor
					try {
						successorChanged = connectToSuccessorList(null);
					}
					catch (IOException e) {
						logger.log(Level.WARNING, "connectToSuccessor() failed.", e);
					}

					// check whether the predecessor is alive or not
					// note: a node keeps an obsolete predecessor without such confirmation
					if (--this.updatePredecessorFreq <= 0) {
						this.updatePredecessorFreq = config.getUpdatePredecessorFreq();	// reset

						updatePredecessor();
					}

					// sleep
					this.sleep(successorChanged);

					if (config.getUseTimerInsteadOfThread()) return;
				}	// while (true)
			}
			catch (InterruptedException e) {
				logger.log(Level.WARNING, "Stabilizer interrupted and die.", e);
			}
		}

		private void sleep(boolean successorChanged) throws InterruptedException {
			// determine the next interval

			if (successorChanged
					|| selfIDAddress.equals(predecessor)) {
				this.interval = config.getStabilizeMinInterval();
			}
			else {
				this.interval <<= 1;
				if (this.interval > config.getStabilizeMaxInterval()) {
					this.interval = config.getStabilizeMaxInterval();
				}
			}

			double playRatio = config.getStabilizeIntervalPlayRatio();
			double intervalRatio = 1.0 - playRatio + (playRatio * 2.0 * random.nextDouble());

			// sleep
			long sleepPeriod = (long)(this.interval * intervalRatio);

			if (config.getUseTimerInsteadOfThread()) {
				timer.schedule(this, Timer.currentTimeMillis() + sleepPeriod,
						true /*isDaemon*/, true /*executeConcurrently*/);
			}
			else {
				Thread.sleep(sleepPeriod);
			}
		}
	}
}
