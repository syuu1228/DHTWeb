/*
 * Copyright 2006,2009 National Institute of Advanced Industrial Science
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

package ow.routing;

import java.math.BigInteger;

import ow.id.ID;
import ow.id.IDAddressPair;

/**
 * An interface through which a routing runtime works on a routing algorithm.
 *
 * @see ow.routing.RoutingAlgorithmProvider#getAlgorithmInstance(RoutingAlgorithmConfiguration)
 */
public interface RoutingAlgorithm {
	/**
	 * Returns the distance between given two IDs.
	 *
	 * @param to target.
	 * @param from starting point.
	 */
	BigInteger distance(ID to, ID from);

	/**
	 * Returns the initial routing context.
	 */
	RoutingContext initialRoutingContext(ID target);

	/**
	 * Returns nodes (ID and address pairs) which are closest to the specified ID.
	 * The number of pairs is the given number or less than it.
	 * Results may include this node itself.
	 * Note that all returned nodes are not always alive.
	 */
	IDAddressPair[] closestTo(ID target, int maxNumber, RoutingContext context);

	/**
	 * Returns a list of nodes (ID and address pairs)
	 * which are candidates for the root node for the specified ID.
	 * In other words, the nodes are "neighbors" onto which this node replicates key-value pairs.
	 * Note that returned array should include the node itself.
	 */
	IDAddressPair[] rootCandidates(ID target, int maxNumber);

	/**
	 * Adjusts the root node.
	 * This method is invoked on the nearest node calculated by
	 * {@link #closestTo(ID, int, RoutingContext) closestNodes()}.
	 *
	 * In case of Chord, the root of a routing is the successor of the specified ID,
	 * but {@link #closestTo(ID, int, RoutingContext) closestNodes()} returns the closest predecessor of the ID.
	 * This method returns the closest successor of the ID based on the predecessor (tentativeLastHop).
	 *
	 * @return candidates for the true last node in the order of priority. null if adjustment is not required.
	 */
	IDAddressPair[] adjustRoot(ID target);

	/**
	 * This method is called on the joining node.
	 * Note that the first element of the given neighbors is the root node for the ID of this node.
	 *
	 * @param neighbors nodes which should be closest from the joining node itself.
	 */
	void join(IDAddressPair[] neighbors);

	/**
	 * Notifies the algorithm instance of a joining node.
	 * This method is called on every node along the resolved route.
	 *
	 * @param joiningNode an IDAddressPair of the joining node.
	 * @param lastHop the last hop of this node. possibly null.
	 * @param isRootNode true if this node is the root node of the routing.
	 */
	void join(IDAddressPair joiningNode, IDAddressPair lastHop, boolean isRootNode);

	/**
	 * Notifies the algorithm that a node is alive.
	 * For example, this method should be called when an packet was come from it.
	 */
	void touch(IDAddressPair from);

	/**
	 * The algorithm instance forgets the specified node.
	 */
	void forget(IDAddressPair node);

	//
	// Implemented by toolkit-side
	//

	/**
	 * Notifies the algorithm instance of a failure of a node.
	 * Note that each algorithm does not (cannot) implement this method because
	 * {@link ow.routing.impl.AbstractRoutingAlgorithm AbstractRoutingAlgorithm}
	 * implements it. 
	 */
	void fail(IDAddressPair failedNode);

	/**
	 * Returns the configuration object.
	 */
	RoutingAlgorithmConfiguration getConfiguration();

	//
	// For algorithm-internal use
	//

	/**
	 * Judges an existing entry to be replaced with the specified new entry in the routing table.
	 * This method is used internally in each algorithm and not required to be in this interface. 
	 */
	boolean toReplace(IDAddressPair existingEntry, IDAddressPair newEntry);

	//
	// Management
	//

	/**
	 * Reset the routing table.
	 */
	void reset();

	/**
	 * Stops this instance.
	 * This method is called by {@link RoutingService#stop() RoutingService#stop()}
	 * and an application usually does not have to call this method directly.
	 */
	void stop();

	/**
	 * Suspends this instance.
	 */
	void suspend();

	/**
	 * Resumes this instance after suspended.
	 */
	void resume();

	//
	// For debug and tools
	//

	/**
	 * Returns a String representation of the routing table.
	 */
	String getRoutingTableString(int verboseLevel);

	/**
	 * Returns a String representation of the routing table.
	 */
//	String getRoutingTableHTMLString();
}
