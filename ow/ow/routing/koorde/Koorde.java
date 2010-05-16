/*
 * Copyright 2006-2009 Kazuyuki Shudo, and contributors.
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

package ow.routing.koorde;

import java.io.IOException;
import java.io.Serializable;
import java.math.BigInteger;
import java.security.InvalidAlgorithmParameterException;
import java.util.Comparator;
import java.util.logging.Level;

import ow.id.ID;
import ow.id.IDAddressPair;
import ow.id.IDAddressRoutingContextTriplet;
import ow.id.comparator.AlgoBasedFromSrcIDComparator;
import ow.id.comparator.AlgoBasedTowardTargetIDComparator;
import ow.messaging.Message;
import ow.messaging.MessageHandler;
import ow.messaging.Tag;
import ow.routing.RoutingAlgorithmConfiguration;
import ow.routing.RoutingContext;
import ow.routing.RoutingException;
import ow.routing.RoutingHop;
import ow.routing.RoutingResult;
import ow.routing.RoutingService;
import ow.routing.linearwalker.LinearWalker;
import ow.util.HTMLUtil;
import ow.util.Timer;

public final class Koorde extends LinearWalker {
	// messages
	private final Message reqPredecessorMessage;

	private KoordeConfiguration config;

	// configuration and edge
	private final int digitBits;	// k = 2 ^ digitBits
	private ID km;
	private IDAddressPair[] edges;
	private int numEdges;

	// daemon
	private EdgeFixer edgeFixer = null;
	private Thread edgeFixerThread = null;

	protected Koorde(RoutingAlgorithmConfiguration config, RoutingService routingSvc) throws InvalidAlgorithmParameterException {
		super(config, routingSvc);

		try {
			this.config = (KoordeConfiguration)config;
		}
		catch (ClassCastException e) {
			throw new InvalidAlgorithmParameterException("The given config is not KoordeConfiguration.");
		}

		// prepare parameters
		this.digitBits = this.config.getDigitBits();
		this.km = this.selfIDAddress.getID().shiftLeft(this.digitBits);
			// km = m << log_2(k)

		// edge and backups
		this.numEdges = this.config.getNumEdges();
		this.edges = new IDAddressPair[this.numEdges];

		// prepare messages
		this.reqPredecessorMessage =
			KoordeMessageFactory.getReqPredecessorMessage(selfIDAddress);

		// does not invoke an edge maintainer
		//startEdgeFixer();
	}

	private synchronized void startEdgeFixer() {
		if (this.edgeFixer != null) return;	// to avoid multiple invocations

		this.edgeFixer = new EdgeFixer(this.config, this.km);

		if (config.getUseTimerInsteadOfThread()) {
			timer.schedule(this.edgeFixer, Timer.currentTimeMillis(),
					true /*isDaemon*/, true /*executeConcurrently*/);
		}
		else if (this.edgeFixerThread == null){
			this.edgeFixerThread = new Thread(this.edgeFixer);
			this.edgeFixerThread.setName("EdgeFixer on " + selfIDAddress.getAddress());
			this.edgeFixerThread.setDaemon(true);
			this.edgeFixerThread.start();
		}
	}

	private synchronized void stopEdgeFixer() {
		if (this.edgeFixer != null) {
			this.edgeFixer = null;
		}

		if (this.edgeFixerThread != null) {
			this.edgeFixerThread.interrupt();
			this.edgeFixerThread = null;
		}
	}

	public synchronized void reset() {
		super.reset();
		this.edges = new IDAddressPair[this.config.getNumEdges()];
	}

	public synchronized void stop() {
		logger.log(Level.INFO, "Koorde#stop() called.");

		super.stop();
		this.stopEdgeFixer();
	}

	public synchronized void suspend() {
		super.suspend();
		this.stopEdgeFixer();
	}

	public synchronized void resume() {
		super.resume();
		this.startEdgeFixer();
	}

	public RoutingContext initialRoutingContext(ID targetID) {
		int idSize = config.getIDSizeInByte();
		ID selfID = this.selfIDAddress.getID();
		ID successor = this.successorList.first().getID();

		BigInteger selfIDInteger, successorInteger, targetIDInteger;

		selfIDInteger = selfID.toBigInteger();
		successorInteger = successor.toBigInteger();
		targetIDInteger = targetID.toBigInteger();

		// in case that there is no successor
		if (this.selfIDAddress.getID().equals(successor)) {
			// there is no successor
			return new KoordeRoutingContext(targetID, selfID, this.digitBits);
		}

		// init comparator
		Comparator<ID> fromSelfComparator = new AlgoBasedFromSrcIDComparator(this, selfID);

		// calculates the length of common bits of m and m.successor
		int i = 0;
		for (i = 0; i < this.idSizeInBit; i++) {
			if (selfIDInteger.testBit(this.idSizeInBit - 1 - i)
					!= successorInteger.testBit(this.idSizeInBit - 1 - i)) {
				break;
			}
		}
		i /= this.digitBits;

		int iter = (this.idSizeInBit - 1) / this.digitBits + 1;
		for (; i <= iter; i++) {
			int shiftWidth = this.idSizeInBit - this.digitBits * i;
			BigInteger mask = BigInteger.ONE.shiftLeft(shiftWidth).subtract(BigInteger.ONE);
			BigInteger topBitsOfK = targetIDInteger.shiftRight(this.digitBits * i);
/*
System.out.println("("+i+")");
System.out.println("self:  " + selfIDInteger.toString(16));
System.out.println("mask:  " + mask.toString(16));
System.out.println("target:" + targetID);
System.out.println("topBit:" + topBitsOfK.toString(16));
System.out.println("succ:  " + successor);
*/

			BigInteger baseID;
			ID imgID, kshift;

			baseID = selfIDInteger;	// m
			imgID = ID.getID(baseID.andNot(mask).or(topBitsOfK), idSize);
//System.out.println("imgID: " + imgID);
			if (fromSelfComparator.compare(imgID, successor) <= 0) {	// imgID is in (selfID, successor]
				kshift = ID.getID(targetIDInteger.shiftLeft(shiftWidth), idSize);
				return new KoordeRoutingContext(kshift, imgID, this.digitBits);
			}

			baseID = successorInteger;		// m.successor
			imgID = ID.getID(baseID.andNot(mask).or(topBitsOfK), idSize);
			if (fromSelfComparator.compare(imgID, successor) <= 0) {
				kshift = ID.getID(targetIDInteger.shiftLeft(shiftWidth), idSize);
				return new KoordeRoutingContext(kshift, imgID, this.digitBits);
			}

			baseID = selfIDInteger.add(BigInteger.ONE.shiftLeft(shiftWidth));	// m + 1
			imgID = ID.getID(baseID.andNot(mask).or(topBitsOfK), idSize);
			if (fromSelfComparator.compare(imgID, successor) <= 0) {
				kshift = ID.getID(targetIDInteger.shiftLeft(shiftWidth), idSize);
				return new KoordeRoutingContext(kshift, imgID, this.digitBits);
			}

			baseID = successorInteger.subtract(BigInteger.ONE.shiftLeft(shiftWidth));	// m.successor - 1
			imgID = ID.getID(baseID.andNot(mask).or(topBitsOfK), idSize);
			if (fromSelfComparator.compare(imgID, successor) <= 0) {
				kshift = ID.getID(targetIDInteger.shiftLeft(shiftWidth), idSize);
				return new KoordeRoutingContext(kshift, imgID, this.digitBits);
			}
		}

		// could not improve imaginary ID, and start routing with self ID
		return new KoordeRoutingContext(targetID, selfID, this.digitBits);
	}

	public IDAddressPair[] closestTo(ID target /* k */, int maxNum, RoutingContext cxt) {
		KoordeRoutingContext context = (KoordeRoutingContext)cxt;

		ID succ = this.successorList.first().getID();

		ID i = context.getI();
		ID selfID = this.selfIDAddress.getID();
		Comparator<ID> toSuccComparator = new AlgoBasedTowardTargetIDComparator(this, succ);
		IDAddressRoutingContextTriplet[] ret;

/*
System.out.println("closestNodes: " + target);
System.out.println("  i: " + context.getI());
System.out.println("  on " + selfIDAddress);
*/
		if (target.equals(succ)
				|| toSuccComparator.compare(selfID, target /* k */) > 0) {	// order: selfID, target
			// k is in (m,successor]
/*
System.out.println("  I'm in charge.");
System.out.println("    self: " + selfID);
System.out.println("    tgt : " + target);
System.out.println("    succ: " + succ);
*/
			if (this.predecessor != null
					&& !this.predecessor.equals(this.selfIDAddress)) {
				ret = new IDAddressRoutingContextTriplet[2];
				ret[1] = new IDAddressRoutingContextTriplet(this.predecessor, context);
			}
			else {
				ret = new IDAddressRoutingContextTriplet[1];
			}
			ret[0] = new IDAddressRoutingContextTriplet(this.selfIDAddress, context);

			return ret;
		}
		else if (i.equals(succ)
				|| toSuccComparator.compare(selfID, i) > 0) {	// order: selfID, i
			// i is in (m,successor]
			if (edges[0] != null) {
				RoutingContext updatedContext = context.next();
					// kshift = kshift << 1, i = i.topBit(kshift)

				ret = new IDAddressRoutingContextTriplet[edges.length];
				for (int j = 0; j < edges.length; j++) {
					if (edges[j] != null)
						ret[j] = new IDAddressRoutingContextTriplet(edges[j], updatedContext);
				}
/*
System.out.println("  forward via an edge: " + (edges[0] != null ? edges[0] : "(null)"));
System.out.println("    self: " + selfID);
System.out.println("    i   : " + i);
System.out.println("    succ: " + succ);
*/

				return ret;
			}
			else {
				// an edge has not been prepared
				context = null;
/*
System.out.println("  no edge!");
*/
			}
		}

		Comparator<ID> fromSelfComparator =
			new AlgoBasedFromSrcIDComparator(this, selfID);
		ID cutPoint;
		if (fromSelfComparator.compare(target, i) < 0)
			cutPoint = target;
		else
			cutPoint = i;

		IDAddressPair[] succList = this.successorList.closestNodes(cutPoint, false);
			// optimized as calling closestNodes(i or target),
			// not successorList.toArray()
		ret = new IDAddressRoutingContextTriplet[succList.length];
		for (int j = 0; j < succList.length; j++) {
			ret[j] = new IDAddressRoutingContextTriplet(succList[j], context);
		}
/*
System.out.println("  forward to a successor: " + (succList[0] != null ? succList[0] : "(null)"));
System.out.println("    tgt : " + target);
System.out.println("    i   : " + i);
*/

		return ret;
	}

	public void forget(IDAddressPair failedNode) {
		super.forget(failedNode);

		this.forget(this.edges, failedNode);
	}

	private void forget(IDAddressPair[] edges, IDAddressPair failedNode) {
		synchronized (this.edges) {
			// pack edges
			int nEdges = this.edges.length;
			for (int i = 0; i < nEdges; i++) {
				if (failedNode.equals(this.edges[i])) {
					int j = i;
					for (j = i; j < i - 1; j++)
						this.edges[j] = this.edges[j + 1];
					this.edges[j] = null;
				}
			}
		}
	}

	public String getRoutingTableString(int verboseLevel) {
		StringBuilder sb = new StringBuilder();

		sb.append(super.getRoutingTableString(verboseLevel));	// successors and predecessor
		sb.append("\n");

		sb.append((1 << this.digitBits) + "m: ");
		sb.append(this.km.toString(verboseLevel));
		sb.append("\n");

		sb.append("de Bruijn edge and backups: [");
		for (int i = 0; i < this.config.getNumEdges(); i++) {
			if (this.edges[i] == null) break;

			sb.append("\n ");
			sb.append(this.edges[i].toString(verboseLevel));
		}
		sb.append("\n]");

		return sb.toString();
	}

	public String getRoutingTableHTMLString() {
		StringBuilder sb = new StringBuilder();

		sb.append(super.getRoutingTableHTMLString());	// successors and predecessor

		sb.append("<h4>" + HTMLUtil.stringInHTML(Integer.toString(1 << this.digitBits)) + "m</h4>\n");
		sb.append(HTMLUtil.stringInHTML(this.km.toString()) + "\n");

		sb.append("<h4>De Bruijn edges and backups</h4>\n");
		for (int i = 0; i < this.config.getNumEdges(); i++) {
			if (this.edges[i] == null) break;

			String url = HTMLUtil.convertMessagingAddressToURL(this.edges[i].getAddress());
			sb.append("<a href=\"" + url + "\">" + HTMLUtil.stringInHTML(url) + "</a><br>\n");
		}

		return sb.toString();
	}

	public void prepareHandlers() {
		super.prepareHandlers();

		// REQ_PREDECESSOR
		MessageHandler handler = new MessageHandler() {
			public Message process(Message msg) {
				return KoordeMessageFactory.getRepPredecessorMessage(
						Koorde.this.selfIDAddress,
						Koorde.this.predecessor);
			}
		};
		runtime.addMessageHandler(Tag.REQ_PREDECESSOR.getNumber(), handler);
	}

	private class EdgeFixer implements Runnable {
		private long interval = config.getFixEdgeMinInterval();
		private ID km;

		EdgeFixer(KoordeConfiguration config, ID km) {
			this.km = km;	// km = m << log_2(k)
		}

		public void run() {
			try {
				while (true) {
					synchronized (Koorde.this) {
						if (stopped || suspended) {
							Koorde.this.edgeFixer = null;
							Koorde.this.edgeFixerThread = null;
							break;
						}
					}

					boolean backupsToBeUpdated = true;
					try {
						// update an edge
						RoutingResult res = runtime.routeToClosestNode(this.km, 1);
						RoutingHop[] route = res.getRoute();
						IDAddressPair oldEdge = edges[0];
						try {
							edges[0] = ((IDAddressRoutingContextTriplet)route[route.length - 1].getIDAddressPair())
									.getIDAddressPair();
						}
						catch (ClassCastException e) {
							// route[route.length - 1] is an IDAddessPair object
							edges[0] = route[route.length - 1].getIDAddressPair();
						}

						int nEdges = edges.length;
						backupsToBeUpdated =
							(edges[0] != null)
							&& (!edges[0].equals(oldEdge) || edges[nEdges - 1] == null);

						if (backupsToBeUpdated) {
							Message reqMsg = Koorde.this.reqPredecessorMessage;

							// copy edges to update
							IDAddressPair[] updatedEdges = new IDAddressPair[edges.length];
							synchronized (edges) {
								System.arraycopy(edges, 0, updatedEdges, 0, edges.length);
							}

							// edges[i + 1] = edges[i].predecessor
							for (int i = 0; i < nEdges - 1; i++) {
								Message repMsg = null;

								if (updatedEdges[i] == null) break;	// give up

								IDAddressPair pred;

								if (updatedEdges[i].equals(Koorde.this.selfIDAddress)) {
									// query receiver (edges[i]) is this node itself
									pred = Koorde.this.predecessor;
								}
								else {
									try {
										repMsg = Koorde.this.sender.sendAndReceive(
												updatedEdges[i].getAddress(), reqMsg);
									}
									catch (IOException e) {
										logger.log(Level.WARNING, "Failed to send a REQ_PREDECESSOR msg or receive a REP_PREDECESSOR msg.", e);
										Koorde.this.forget(updatedEdges, updatedEdges[i]);
										i--;
										continue;
									}

									Serializable[] contents = repMsg.getContents();
									pred = (IDAddressPair)contents[0];
								}

								if (updatedEdges[i].equals(pred)) {
									// query receiver itself is its predecessor
									break;
								}

								updatedEdges[i + 1] = pred;
							}

							// write back updated edges
							synchronized (edges) {
								System.arraycopy(updatedEdges, 0, edges, 0, edges.length);
							}
						}	// if (backupToBeUpdated)
					}
					catch (RoutingException e) {
						logger.log(Level.WARNING, "Routing failed.", e);
					}

					// sleep
					if (this.sleep(backupsToBeUpdated)) return;
				}	// while (true)
			}
			catch (InterruptedException e) {
				logger.log(Level.WARNING, "EdgeFixer interrupted and die.", e);
			}
		}

		private boolean sleep(boolean backupsToBeUpdated) throws InterruptedException {
			if (backupsToBeUpdated) {
				this.interval = config.getFixEdgeMinInterval();
			}
			else {
				this.interval <<= 1;
				if (this.interval > config.getFixEdgeMaxInterval()) {
					this.interval = config.getFixEdgeMaxInterval();
				}
			}

			// sleep
			double playRatio = config.getFixEdgeIntervalPlayRatio();
			double intervalRatio = 1.0 - playRatio + (playRatio * 2.0 * random.nextDouble());

			long sleepPeriod = (long)(this.interval * intervalRatio);
			if (config.getUseTimerInsteadOfThread()) {
				timer.schedule(this, Timer.currentTimeMillis() + sleepPeriod,
						true /*isDaemon*/, true /*executeConcurrently*/);
				return true;
			}
			else {
				Thread.sleep(sleepPeriod);
				return false;
			}
		}
	}
}
