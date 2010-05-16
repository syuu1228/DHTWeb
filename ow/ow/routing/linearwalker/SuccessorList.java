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

package ow.routing.linearwalker;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.SortedSet;
import java.util.TreeSet;

import ow.id.ID;
import ow.id.IDAddressPair;
import ow.id.comparator.AlgoBasedFromSrcIDAddrPairComparator;

public final class SuccessorList {
	private final IDAddressPair selfIDAddress;
	private final int maxLength;
	private final SortedSet<IDAddressPair> list;
	private final LinearWalker algo;

	/**
	 * Constructor.
	 *
	 * @param maxLength infinity if equal to or less than 0
	 */
	public SuccessorList(LinearWalker algo, IDAddressPair selfIDAddress, int maxLength) {
		this.algo = algo;
		this.selfIDAddress = selfIDAddress;
		this.maxLength = maxLength;
		this.list = new TreeSet<IDAddressPair>(
				new AlgoBasedFromSrcIDAddrPairComparator(algo, selfIDAddress.getID()));

		// initialize
		this.list.add(selfIDAddress);	// a SuccessorList instance always contains the node itself
	}

	synchronized void clear() {
		this.list.clear();
		this.list.add(selfIDAddress);
	}

	public void add(IDAddressPair elem) {
		if (elem == null) return;

		synchronized (this.list) {
			boolean added = this.list.add(elem);

			if (added && maxLength > 0) {
				while (this.list.size() > maxLength) {
					IDAddressPair lastElem = this.list.last();
					this.list.remove(lastElem);
				}
			}
		}
	}

	public void addAll(IDAddressPair[] elems) {
		if (elems == null) return;

		synchronized (this.list) {
			boolean added = false;

			for (IDAddressPair elem: elems) {
				added |= this.list.add(elem);
			}

			if (added && maxLength > 0) {
				while (this.list.size() > maxLength) {
					IDAddressPair lastElem = this.list.last();
					this.list.remove(lastElem);
				}
			}
		}
	}

	public boolean contains(IDAddressPair elem) {
		if (elem == null) return false;

		return this.list.contains(elem);
	}

	public boolean remove(IDAddressPair elem) {
		boolean ret;

		synchronized (this.list) {
			ret = this.list.remove(elem); 

			if (this.list.isEmpty()) {
				this.list.add(selfIDAddress);
			}
		}

		return ret;
	}

	public IDAddressPair first() {
		synchronized (this.list) {
			try {
				return this.list.first();	// throws NoSuchElementException
			}
			catch (NoSuchElementException e) {
				this.list.add(selfIDAddress);
				return selfIDAddress;
			}
		}
	}

	public boolean isEmpty() {
		return this.list.isEmpty();
	}

	public IDAddressPair[] toArray() {
		synchronized (this.list) {
			return this.list.toArray(new IDAddressPair[0]);
		}
	}

	IDAddressPair[] rootCandidates(ID target, int maxLen, IDAddressPair predecessor) {
		BigInteger targetMinusOneInt = target.toBigInteger().subtract(BigInteger.ONE);
		if (targetMinusOneInt.compareTo(BigInteger.ZERO) < 0) {
			targetMinusOneInt = targetMinusOneInt.add(this.algo.sizeOfIdSpace);
		}
		ID targetMinusOne = ID.getID(targetMinusOneInt, target.getSize());

		SortedSet<IDAddressPair> retSet = new TreeSet<IDAddressPair>(
					new AlgoBasedFromSrcIDAddrPairComparator(algo, targetMinusOne));

		// add all candidates
		retSet.add(selfIDAddress);
			// It is possible that this.list does not contain the node itself. 

		synchronized (this.list) {
			retSet.addAll(this.list);
		}

		if (predecessor != null)
			retSet.add(predecessor);

//System.out.println("target - 1         : " + targetMinusOne);
//System.out.println("root candidates for: " + target);
//for (IDAddressPair p: retSet) {
//	System.out.println(" " + p);
//	System.out.println("    dist: " + algo.distance(p.getID(), targetMinusOne).toString(16));
//}
		// convert to an array
		int len = Math.min(maxLen, retSet.size());
		IDAddressPair[] ret = new IDAddressPair[len];

		int i = 0;
		for (IDAddressPair p: retSet) {
			if (i >= len) break;

			ret[i++] = p;
		}

		return ret;
	}

	public IDAddressPair[] closestNodes(ID target, boolean includeSelf) {
		IDAddressPair toElement = IDAddressPair.getIDAddressPair(target, null);
		List<IDAddressPair> result = new ArrayList<IDAddressPair>();

		synchronized (this.list) {
			SortedSet<IDAddressPair> smallerHalf, largerHalf;

			smallerHalf = this.list.headSet(toElement);
			largerHalf = this.list.tailSet(toElement);	// may include target

			if (!includeSelf) {
				smallerHalf.remove(this.selfIDAddress);
				largerHalf.remove(this.selfIDAddress);
			}

			result.addAll(largerHalf);	// iterates over largerHalf, which is to be locked 

			if (includeSelf && !this.list.contains(selfIDAddress))
				result.add(selfIDAddress);

			result.addAll(smallerHalf);
		}

		// reverse the list
		int len = result.size();
		IDAddressPair[] ret = new IDAddressPair[len];
		for (int i = 0; i < len; i++) {
			ret[i] = result.get(len - 1 - i);
		}

//System.out.println("SuccessorList#closestNodes:");
//for (int i = 0; i < len; i++) System.out.println(" " + ret[i]);
//System.out.println();

		return ret;
	}
}
