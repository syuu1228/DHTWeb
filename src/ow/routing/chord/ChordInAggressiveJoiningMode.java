/*
 * Copyright 2006-2008 National Institute of Advanced Industrial Science
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

package ow.routing.chord;

import java.io.IOException;
import java.io.Serializable;
import java.math.BigInteger;
import java.security.InvalidAlgorithmParameterException;
import java.util.Comparator;
import java.util.NoSuchElementException;
import java.util.logging.Level;

import ow.id.ID;
import ow.id.IDAddressPair;
import ow.messaging.Message;
import ow.messaging.MessageHandler;
import ow.messaging.Tag;
import ow.routing.RoutingAlgorithmConfiguration;
import ow.routing.RoutingException;
import ow.routing.RoutingHop;
import ow.routing.RoutingResult;
import ow.routing.RoutingService;

/**
 * A Chord implementation which completes construction of routing table when joining.
 * This algorithm is described in Figure 6 in the Chord paper
 * "Chord: A Scalable Peer-to-peer Lookup Service for Internet Applications".
 * Note that this algorithm does not allow concurrent joinings by multiple nodes.
 */
public final class ChordInAggressiveJoiningMode extends AbstractChord {
	// messages
	private final Message ackFingerTableMessage;

	protected ChordInAggressiveJoiningMode(RoutingAlgorithmConfiguration config, RoutingService routingSvc)
			throws InvalidAlgorithmParameterException {
		super(config, routingSvc);

		// prepare messages
		this.ackFingerTableMessage = ChordMessageFactory.getAckFingerTableMessage(selfIDAddress);
	}

	public void join(IDAddressPair[] neighbors) {	// overriding LinearWalker#join
		IDAddressPair rootNode = neighbors[0];	// should be successor

		// init_finger_table()

		// ask predecessor and successors of the root node (successor)
		super.join(neighbors);	// call this.connectoToSuccessor(rootNode)

		// fill the finger table
		IDAddressPair rootNodeOfFingerEnd = rootNode;
		BigInteger rootNodeDistance = distance(rootNodeOfFingerEnd.getID(), selfIDAddress.getID());

		this.fingerTable.set(1, rootNodeOfFingerEnd);
		this.successorList.add(rootNodeOfFingerEnd);

		logger.log(Level.INFO, "join() performs \"init_finger_table()\".");
		logger.log(Level.INFO, "i=1: " + rootNodeOfFingerEnd.getAddress());

		for (int i = 2; i <= this.idSizeInBit; i++) {
			BigInteger fingerEndDistance = BigInteger.ONE.shiftLeft(i - 1);	// 2 ^ (i-1)

			if (rootNodeDistance.compareTo(fingerEndDistance) < 0) {
				// lastEntryDistance < fingerEndDistance
				// and update lastEntry

				BigInteger fingerEndIDValue = selfIDAddress.getID().toBigInteger().add(fingerEndDistance); 
				ID fingerEndID = ID.getID(fingerEndIDValue, config.getIDSizeInByte());

				// update rootNodeOfFingerEnd
				try {
					RoutingResult res = runtime.routeToRootNode(fingerEndID, 1);

					RoutingHop[] routeToFingerEnd = res.getRoute();
					rootNodeOfFingerEnd = routeToFingerEnd[routeToFingerEnd.length - 1].getIDAddressPair();
					rootNodeDistance = distance(rootNodeOfFingerEnd.getID(), selfIDAddress.getID());

					logger.log(Level.INFO, "i=" + i + ": " + rootNodeOfFingerEnd.getAddress());
				}
				catch (RoutingException e) {
					logger.log(Level.WARNING, "Routing failed.", e);
				}
			}

			this.fingerTable.set(i, rootNodeOfFingerEnd);
			this.successorList.add(rootNodeOfFingerEnd);
		}

		// update_others()
		logger.log(Level.INFO, "join() performs \"update_others()\".");

		BigInteger selfIDBigInteger = selfIDAddress.getID().toBigInteger();

		IDAddressPair lastTarget = null;

		for (int i = 1; i <= this.idSizeInBit; i++) {
//System.out.println("  i=" + i);
			BigInteger targetIDBigInteger =
				selfIDBigInteger.subtract(BigInteger.ONE.shiftLeft(i - 1).subtract(BigInteger.ONE));
					// n - (2 ^ (i-1) - 1)
					// Figure 6: p = find_predecessor (n - 2 ^ (i-1)) is incorrect.
			ID targetID = ID.getID(targetIDBigInteger, config.getIDSizeInByte());

			IDAddressPair predecessorOfTarget;
			if (true) {
				// remote
				RoutingResult res;
				try {
					res = runtime.routeToClosestNode(targetID, 1);
				}
				catch (RoutingException e) {
					logger.log(Level.WARNING, "Routing failed.", e);
					continue;
				}

				RoutingHop[] route = res.getRoute();
				predecessorOfTarget = route[route.length - 1].getIDAddressPair();
			}
			else {
				// local
				IDAddressPair[] predecessorsToTarget = closestTo(targetID, 1, null);
				predecessorOfTarget = predecessorsToTarget[predecessorsToTarget.length - 1];
			}
//System.out.println("  predOfTarget: " + predecessorOfTarget);
//System.out.println("  lastTarget  : " + lastTarget);

			if (!predecessorOfTarget.equals(lastTarget)) {
				if (i > 2) {
					if (!selfIDAddress.equals(lastTarget)) {	// do not send to this node itself
//System.out.println("send UPDATE_FINGER_TABLE(" + lastTarget + ", " + (i - 1));
						this.sendUpdateFingerTableMessage(lastTarget, i - 1);
					}
				}
				lastTarget = predecessorOfTarget;
			}
		}

		if (!selfIDAddress.equals(lastTarget)) {	// do not send to this node itself
//System.out.println("send UPDATE_FINGER_TABLE(" + lastTarget + ", " + idSizeInBit);
			this.sendUpdateFingerTableMessage(lastTarget, this.idSizeInBit);
		}

		logger.log(Level.INFO, "join() completed.");

//		return succeed;
	}

	private void sendUpdateFingerTableMessage(IDAddressPair target, int largestIndex) {
		Message reqMsg =
			ChordMessageFactory.getUpdateFingerTableMessage(selfIDAddress, largestIndex);

		try {
			Message repMsg = sender.sendAndReceive(target.getAddress(), reqMsg);

			if (repMsg.getTag() != Tag.ACK_FINGER_TABLE.getNumber()) {
					logger.log(Level.SEVERE, "A reply to an UPDATE_FINGER_TABLE message is not an ACK_FINGER_TABLE.");
			}
		}
		catch (IOException e) {
			logger.log(Level.WARNING, "Failed to send an UPDATE_FINGER_TABLE request or receive a reply.", e);
			this.fail(target);
		}
	}

	public void forget(IDAddressPair failedNode) {
		IDAddressPair oldSuccessor, newSuccessor;

		oldSuccessor = this.successorList.first();

		super.forget(failedNode);

		try {
			newSuccessor = this.successorList.first();
		}
		catch (NoSuchElementException e) { return; }

		if (!newSuccessor.equals(oldSuccessor)) {
			boolean succeed = false;
			do {
				try {
					IDAddressPair predOfSucc = super.connectToSuccessor(newSuccessor, true);
					this.predecessor = predOfSucc;

					succeed = true;
				}
				catch (IOException e0) {
					this.successorList.remove(newSuccessor);
					try {
						newSuccessor = this.successorList.first();
					}
					catch (NoSuchElementException e1) { return; }
				}
			} while (!succeed);
		}
	}

	public void prepareHandlers() {
		super.prepareHandlers(true);

		MessageHandler handler;

		// REQ_CONNECT
		// first half of init_finger_table(n')
		handler = new ReqConnectMessageHandler(towardSelfComparator);
		runtime.addMessageHandler(Tag.REQ_CONNECT.getNumber(), handler);

		// UPDATE_FINGER_TABLE
		// update_finger_table(s,i)
		handler = new MessageHandler() {
			public Message process(final Message msg) {
				algorithm.touch(msg.getSource());	// notify the algorithm

				Runnable r = new Runnable() {
					public void run() {
						Serializable[] contents = msg.getContents();
						int largestIndex = (Integer)contents[0];
						IDAddressPair candidateNode = msg.getSource();

						IDAddressPair existingNode = fingerTable.get(largestIndex);

						BigInteger distanceOfCandidate = distance(candidateNode.getID(), selfIDAddress.getID());
						BigInteger distanceOfExisting = distance(existingNode.getID(), selfIDAddress.getID());

//System.out.println("largestIndex: " + largestIndex);
//System.out.println("  existing: " + existingNode);
//System.out.println("    " + distanceOfExisting.toString(16));
//System.out.println("  candidate: " + candidateNode);
//System.out.println("    " + distanceOfCandidate.toString(16));
						if (distanceOfCandidate.compareTo(distanceOfExisting) < 0) {
//System.out.println("    candidate is nearer.");
							// candidate is nearer than existing

							fingerTable.put(candidateNode, largestIndex);
							successorList.add(candidateNode);
//System.out.println("On " + selfIDAddress.getAddress() + ", succ list.add: " + candidateNode);

							if (!predecessor.equals(candidateNode)) {
								// do not forward if the predecessor is the initiator of the message

								try {
									Message repMsg = sender.sendAndReceive(
											predecessor.getAddress(), msg);

									if (repMsg.getTag() != Tag.ACK_FINGER_TABLE.getNumber()) {
										logger.log(Level.SEVERE, "A reply to an UPDATE_FINGER_TABLE message is not an ACK.");
									}
								}
								catch (IOException e) {
									logger.log(Level.WARNING, "Failed to send an <NGER_TABLE request or receive a reply.", e);
									fail(predecessor);
								}
							}
						}
					}
				};
				Thread t = new Thread(r);
				t.setName("A MessageHandler of Chord");
				t.setDaemon(true);
				t.setPriority(Thread.currentThread().getPriority() - 1);
				t.start();

				// reply an ACK
				return ChordInAggressiveJoiningMode.this.ackFingerTableMessage;
			}
		};
		runtime.addMessageHandler(Tag.UPDATE_FINGER_TABLE.getNumber(), handler);
	}
		
	class ReqConnectMessageHandler extends AbstractChord.ReqConnectMessageHandler {
		ReqConnectMessageHandler(Comparator<IDAddressPair> comparator) {}

		public Message process(Message msg) {
			Message repMsg = super.process(msg);

			// update predecessor
			predecessor  = msg.getSource();

			return repMsg;
		}
	}
}
