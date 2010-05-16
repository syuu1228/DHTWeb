/*
 * Copyright 2006 National Institute of Advanced Industrial Science
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

package ow.id.comparator;

import java.util.Comparator;

import ow.id.ID;
import ow.id.IDAddressPair;
import ow.routing.RoutingAlgorithm;

public final class AlgoBasedFromSrcIDAddrPairComparator implements Comparator<IDAddressPair> {
	Comparator<ID> comparator;

	public AlgoBasedFromSrcIDAddrPairComparator(RoutingAlgorithm algo, ID sourceID) {
		this.comparator = new AlgoBasedFromSrcIDComparator(algo, sourceID);
	}

	public int compare(IDAddressPair p1, IDAddressPair p2) {
		return this.comparator.compare(p1.getID(), p2.getID());
	}
}
