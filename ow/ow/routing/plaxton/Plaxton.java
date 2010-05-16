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

package ow.routing.plaxton;

import java.math.BigInteger;
import java.security.InvalidAlgorithmParameterException;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;

import ow.id.ID;
import ow.id.IDAddressPair;
import ow.routing.RoutingAlgorithmConfiguration;
import ow.routing.RoutingContext;
import ow.routing.RoutingService;
import ow.routing.impl.AbstractRoutingAlgorithm;

public abstract class Plaxton extends AbstractRoutingAlgorithm {
	private PlaxtonConfiguration config;
	protected boolean stopped = false;
	protected boolean suspended = true;

	// routing table
	protected final int idSizeInBit;
	protected final int idSizeInDigit;
	protected final int digitSize;
	protected final RoutingTable routingTable;

	protected Plaxton(RoutingAlgorithmConfiguration config, RoutingService routingSvc)
			throws InvalidAlgorithmParameterException {
		super(config, routingSvc);

		try {
			this.config = (PlaxtonConfiguration)config;
		}
		catch (ClassCastException e) {
			throw new InvalidAlgorithmParameterException("The given config is not KademliaConfiguration.");
		}

		this.idSizeInBit = config.getIDSizeInByte() * 8;
		this.idSizeInDigit = (this.idSizeInBit - 1) / this.config.getDigitSize() + 1;
		this.digitSize = this.config.getDigitSize();

		// prepare routing table
		if (routingSvc != null) {
			this.routingTable = new RoutingTable(idSizeInDigit, digitSize,
					routingSvc.getSelfIDAddressPair(), this);
		}
		else {
			logger.log(Level.SEVERE, "routingSvc is null. test?");
			this.routingTable = null;
		}
	}

	public void reset() {
		this.routingTable.clear();
	}

	public IDAddressPair[] closestTo(ID target, int maxNum, RoutingContext cxt) {
		int nMatchBits = ID.matchLengthFromMSB(selfIDAddress.getID(), target);
		int nMatchDigits = nMatchBits / digitSize;
		int notMatchDigit = getDigit(target, nMatchDigits);

		// traverse downward

		Collection<IDAddressPair> results = null;
		if (nMatchDigits < idSizeInDigit) {
			results = traverseDownward(nMatchDigits, notMatchDigit, target, maxNum);
/*
System.out.println("On " + selfIDAddress.getAddress());
System.out.println("  nMatchBits, nMatchDigits, notMatchDigit: " + nMatchBits + "," + nMatchDigits + "," + notMatchDigit);
System.out.print("  closestNodes:")getDigit;
for (IDAddressPair p: results) System.out.print(" " + p.getAddress());
System.out.println();
System.out.flush();
*/
		}

		IDAddressPair[] downwardNodes;
		if (results != null && results.size() > 0) {
			downwardNodes = new IDAddressPair[results.size()];
			results.toArray(downwardNodes);
		}
		else {
			downwardNodes = new IDAddressPair[1];
			downwardNodes[0] = selfIDAddress;
		}

		if (downwardNodes.length >= maxNum)
			return downwardNodes;

		// traverse upward
		// in case # of neighbors is insufficient

		IDAddressPair[] upwardNodes = null;

//		if (traverseFartherThanSelf) {
			List<IDAddressPair> upwardNodesList =
				traverseUpward(nMatchDigits, maxNum - downwardNodes.length);
			upwardNodes = new IDAddressPair[upwardNodesList.size()];
			upwardNodesList.toArray(upwardNodes);
//		}

		// summarize
		int numUpwardNodes = (upwardNodes == null ? 0 : upwardNodes.length);
		IDAddressPair[] ret = new IDAddressPair[downwardNodes.length + numUpwardNodes];
		System.arraycopy(downwardNodes, 0, ret, 0, downwardNodes.length);
		if (upwardNodes != null) {
			System.arraycopy(upwardNodes, 0, ret, downwardNodes.length, numUpwardNodes);
		}

		return ret;
	}

	public IDAddressPair[] rootCandidates(ID target, int maxNum) {
		return this.closestTo(target, maxNum, null);
	}

	protected abstract List<IDAddressPair> traverseDownward(int rowIndex, int startingCol, ID target, int maxNum);
	protected abstract List<IDAddressPair> traverseUpward(int rowIndex, int maxNum);

	public void stop() { this.stopped = true; }

	public synchronized void suspend() {
		this.suspended = true;
	}

	public synchronized void resume() {
		this.suspended = false;

		this.notifyAll();
	}

	public IDAddressPair[] adjustRoot(ID rootCandidate) {
		// do nothing
		return null;
	}

	public boolean toReplace(IDAddressPair existingEntry, IDAddressPair newEntry) {
		// TODO
		// To be improved.
		// Pastry should consider proximity.
		// Tapestry shows no idea on how to judge this.

		return random.nextDouble() < config.getReplaceProbability();
	}

	public void join(IDAddressPair[] neighbors) {
		this.resume();
	}

	public void join(IDAddressPair joiningNode, IDAddressPair lastHop, boolean isFinalNode) {
		ID joiningNodeID = (joiningNode != null ? joiningNode.getID() : null);

		if (!this.selfIDAddress.getID().equals(joiningNodeID)) {
			// joining node is not this node itself
			this.resume();
		}
	}

	public void touch(IDAddressPair from) {
		if (config.getUpdateRoutingTableByAllCommunications()) {
			this.routingTable.merge(from);
		}
	}

	public void forget(IDAddressPair failedNode) {
		this.routingTable.remove(failedNode);
	}

	public String getRoutingTableString(int verboseLevel) {
		return this.routingTable.toString(verboseLevel);
	}

	public String getRoutingTableHTMLString() {
		StringBuilder sb = new StringBuilder();

		sb.append("<h4>Plaxton Routing Table</h5>\n");
		sb.append(this.routingTable.toHTMLString());

		return sb.toString();
	}

	protected void prepareHandlers() {
		// do nothing
	}

	//
	// Utility methods
	//

	public int getDigit(ID id, int index) {
		return id.getBits(this.idSizeInBit - ((index + 1) * this.digitSize), this.digitSize);
	}

	public ID setDigit(ID id, int index, int digit) {
		int bitOffset = this.idSizeInBit - ((index + 1) * this.digitSize);

		BigInteger mask = BigInteger.valueOf(~(~0 << this.digitSize));
		mask = mask.shiftLeft(bitOffset);

		BigInteger digitInt = BigInteger.valueOf(digit);
		digitInt = digitInt.shiftLeft(bitOffset);

		BigInteger v = id.toBigInteger();
		v = v.andNot(mask);
		v = v.or(digitInt);

		return ID.getID(v, id.getSize());
	}
}
