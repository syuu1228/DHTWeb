/*
 * Copyright 2006-2007,2009 National Institute of Advanced Industrial Science
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

import java.math.BigInteger;
import java.security.InvalidAlgorithmParameterException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import ow.id.ID;
import ow.id.IDAddressPair;
import ow.id.comparator.AlgoBasedTowardTargetIDAddrComparator;
import ow.messaging.Message;
import ow.routing.RoutingAlgorithmConfiguration;
import ow.routing.RoutingContext;
import ow.routing.RoutingService;
import ow.routing.linearwalker.LinearWalker;
import ow.util.HTMLUtil;

public abstract class AbstractChord extends LinearWalker {
	// routing table
	final FingerTable fingerTable;

	protected AbstractChord(RoutingAlgorithmConfiguration config, RoutingService routingSvc)
			throws InvalidAlgorithmParameterException {
		super(config, routingSvc);

		// initialize routing table
		this.fingerTable = new FingerTable(config.getIDSizeInByte(), this, selfIDAddress,
				this.config.getAggressiveJoiningMode());
	}

	public void reset() {
		super.reset();
		this.fingerTable.clear();
	}

	public IDAddressPair[] closestTo(ID target, int maxNum, RoutingContext cxt) {	// find_predecessor()
//System.out.println("On " + selfIDAddress.getAddress() + ", closestNodes(): " + target);
		List<IDAddressPair> results = new ArrayList<IDAddressPair>();
		int count = 0;

		// init comparator
		Comparator<IDAddressPair> towardTargetComparator =
			new AlgoBasedTowardTargetIDAddrComparator(this, target);

		IDAddressPair fingerTableEntry, successorListEntry, lastEntry = null;

		// initialize a pointer in the finger table
		// find a starting entry better than this node itself
		BigInteger distance = this.distance(target, selfIDAddress.getID());
		int fingerTableIndex = distance.bitLength();
		if (fingerTableIndex > this.idSizeInBit) fingerTableIndex = this.idSizeInBit;	// fix 161 to 160 

		do {
			if (fingerTableIndex <= 0) {
				fingerTableEntry = null;
				break;
			}
			fingerTableEntry = fingerTable.get(fingerTableIndex--);
		} while (towardTargetComparator.compare(selfIDAddress, fingerTableEntry) <= 0);

		// initialize a pointer in the successor list
		IDAddressPair[] successorArray = super.closestTo(target, maxNum, cxt);
			// successor list cut at target

		int successorListIndex = 0;

		successorListEntry = (successorListIndex < successorArray.length ?
				successorArray[successorListIndex++] : null);

		while ((fingerTableEntry != null || successorListEntry != null)
				&& count++ < maxNum) {
			boolean fingerTableChosen;
			if (fingerTableEntry == null) {
				fingerTableChosen = false;
			} else if (successorListEntry == null) {
				fingerTableChosen = true;
			} else { // both are not null
				fingerTableChosen = (towardTargetComparator.compare(
						fingerTableEntry, successorListEntry) <= 0);
			}

			if (fingerTableChosen) {
				if (!fingerTableEntry.equals(lastEntry)) {
					results.add(fingerTableEntry);
					lastEntry = fingerTableEntry;
//System.out.println("add(f): " + lastEntry + " at " + (fingerTableIndex + 1));
				}

				do {
					fingerTableEntry =
						(fingerTableIndex > 0 ? fingerTable.get(fingerTableIndex--) : null);
				} while (lastEntry.equals(fingerTableEntry));

				// skip an entry same as the added entry
				if (lastEntry.equals(successorListEntry)) {
					successorListEntry = (successorListIndex < successorArray.length ?
							successorArray[successorListIndex++] : null);
				}
			}
			else {
				if (!successorListEntry.equals(lastEntry)) {
					results.add(successorListEntry);
					lastEntry = successorListEntry;
//System.out.println("add(s): " + lastEntry);
				}

				successorListEntry = (successorListIndex < successorArray.length ?
						successorArray[successorListIndex++] : null);

				// skip entries same as the added entry
				while (lastEntry.equals(fingerTableEntry)) {
					fingerTableEntry =
						(fingerTableIndex > 0 ? fingerTable.get(fingerTableIndex--) : null);
				}
			}
		}

		int len = results.size();
		IDAddressPair[] ret = new IDAddressPair[len];
		results.toArray(ret);
		return ret;
	}

	public void touch(IDAddressPair from) {
		if (this.config.getUpdateRoutingTableByAllCommunications()) {
			super.touch(from);

			this.fingerTable.put(from);
		}
	}

	public void forget(IDAddressPair failedNode) {
		super.forget(failedNode);

		// finger table
		this.fingerTable.remove(failedNode.getID());
	}

	public String getRoutingTableString(int verboseLevel) {
		StringBuilder sb = new StringBuilder();

		sb.append(super.getRoutingTableString(verboseLevel));	// successors and predecessor
		sb.append("\n");

		sb.append("finger table: [");
		IDAddressPair lastEntry = null;
		for (int i = 1; i <= this.idSizeInBit; i++) {
			IDAddressPair entry = this.fingerTable.get(i);
			if (!entry.equals(lastEntry)) {
				sb.append("\n ").append(i).append(": ").append(entry.toString(verboseLevel));
				lastEntry = entry;
			}
		}
		sb.append("\n]");

		return sb.toString();
	}

	public String getRoutingTableHTMLString() {
		StringBuilder sb = new StringBuilder();

		sb.append(super.getRoutingTableHTMLString());	// successors and predecessor

		sb.append("<h4>Finger Table</h4>\n");
		sb.append("<table>\n");
		IDAddressPair lastEntry = null;
		for (int i = 1; i <= this.idSizeInBit; i++) {
			IDAddressPair entry = this.fingerTable.get(i);
			if (!entry.equals(lastEntry)) {
				String url = HTMLUtil.convertMessagingAddressToURL(entry.getAddress());
				sb.append("<tr><td>" + HTMLUtil.stringInHTML(Integer.toString(i)) + "</td>"
						+ "<td><a href=\"" + url + "\">" + HTMLUtil.stringInHTML(url) + "</a></td>"
						+ "<td>" + HTMLUtil.stringInHTML(entry.getID().toString()) + "</td></tr>\n");
				lastEntry = entry;
			}
		}
		sb.append("</table>\n");

		return sb.toString();
	}

	class ReqConnectMessageHandler extends LinearWalker.ReqConnectMessageHandler {
		public Message process(Message msg) {
			Message repMsg = super.process(msg);

			// try to add the predecessor to finger table and successor list
			// these does not exist in the Figure 6
			fingerTable.put(msg.getSource());

			return repMsg;
		}
	}
}
