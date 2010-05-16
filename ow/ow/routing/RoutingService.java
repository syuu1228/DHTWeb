/*
 * Copyright 2006,2008-2009 National Institute of Advanced Industrial Science
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

import java.io.Serializable;

import ow.id.ID;
import ow.id.IDAddressPair;
import ow.messaging.MessageHandler;
import ow.messaging.MessageSender;
import ow.messaging.MessagingAddress;
import ow.messaging.MessagingProvider;
import ow.stat.MessagingReporter;

/**
 * An interface through which an application (e.g. DHT) works on routing facilities.
 */
public interface RoutingService {
	/**
	 * Joins an overlay.
	 *
	 * @param initialContact the node which this call contacts initially
	 * @return a route and neighbors to myself. null if joining failed.
	 */
	RoutingResult join(MessagingAddress initialContact)
			throws RoutingException;

	/**
	 * Leaves the overlay.
	 * An instance of the routing algorithm is reset.
	 */
	void leave();

	/**
	 * Returns a route to the root node for the target.
	 *
	 * @param target target ID of routing
	 * @param numRootCandidates number of candidates for the root node to be returned
	 * @return a route to / neighbors of the root node. The route includes the starting node itself.
	 */
	RoutingResult routeToRootNode(ID target, int numRootCandidates)
			throws RoutingException;

	/*
	 * Performs multiple routing for the multiple targets,
	 * and returns routes to the root nodes for the targets.
	 *
	 * Note that an element of the returned array is null if routing to a target corresponding the array element failed.
	 */
	RoutingResult[] routeToRootNode(ID[] target, int numRootCandidates);

	/**
	 * Invokes callback method on nodes on the route for the specified target.
	 * On each node on the route callback are invoked with the specified arguments.
	 * This method returns a result of a callback on the root node. 
	 *
	 * @param target target ID of routing
	 * @param numRootCandidates number of candidates for the root node which this call request
	 * @param returnedValueContainer
	 * 		a value returned from a callback is stored at the head ([0]) of this array.
	 * 		null if no value found.
	 * @param filter a filter which judges a value returned from a callback is passed back to the caller.
	 * @param tag tag passed to callback
	 * @param args arguments passed to callback
	 * @return a route to / neighbors of the root node. it includes this starting node itself.
	 */
	RoutingResult invokeCallbacksOnRoute(ID target, int numRootCandidates,
			Serializable[] returnedValueContainer, CallbackResultFilter filter,
			int tag, Serializable[] args)
				throws RoutingException;

	/**
	 * Performs multiple routing for the multiple targets,
	 * and invokes callback method on nodes on the routes for the specified target.
	 *
	 * Note that an element of the returned array is null if routing to a target corresponding the array element failed.
	 */
	RoutingResult[] invokeCallbacksOnRoute(ID[] target, int numRootCandidates,
			Serializable[][] returnedValueContainer, CallbackResultFilter filter,
			int tag, Serializable[][] args);

	/**
	 * Registers a callback, which can be invoked on every node along the resolved route.
	 */
	void addCallbackOnRoute(CallbackOnRoute callback);

	/**
	 * Registers a callback, which is invoked in case of neighbor node failure.
	 */
	void addCallbackOnNodeFailure(CallbackOnNodeFailure callback);

	/**
	 * Registers a MessageHandler associated with the specified tag.
	 */
	void addMessageHandler(int tag, MessageHandler handler);

	//
	// Management
	//

	/**
	 * Stops the service.
	 */
	void stop();
	void suspend();
	void resume();

	//
	// Utility methods
	//

	/**
	 * Returns the IDAddressPair of this service itself.
	 */
	IDAddressPair getSelfIDAddressPair();

	/**
	 * Returns the MessagingProvider which this routing service uses.
	 */
	MessagingProvider getMessagingProvider();

	/**
	 * Returns the MessageSender which this routing service uses.
	 */
	MessageSender getMessageSender();

	/**
	 * Returns the MessagingReporter which this routing service uses.
	 */
	MessagingReporter getMessagingReporter();

	/**
	 * Returns the RoutingAlgorithm which this routing service uses.
	 */
	RoutingAlgorithm getRoutingAlgorithm();

	/**
	 * Sets the address of a statistics collector to which communication status is reported.
	 */
	void setStatCollectorAddress(MessagingAddress address);

	/**
	 * Returns a String representation of a route.
	 */
	String routeToString(RoutingHop[] route);
}
