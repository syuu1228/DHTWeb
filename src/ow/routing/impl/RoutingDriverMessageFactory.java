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

package ow.routing.impl;

import java.io.Serializable;

import ow.id.ID;
import ow.id.IDAddressPair;
import ow.messaging.Message;
import ow.messaging.Tag;
import ow.routing.CallbackResultFilter;
import ow.routing.RoutingContext;
import ow.routing.RoutingHop;
import ow.routing.RoutingResult;

/**
 * An utility class to create a message for routing drivers.
 */
public class RoutingDriverMessageFactory {
	//
	// basic messages
	//

	public static Message getPingMessage(IDAddressPair src) {
		int tag = Tag.PING.getNumber();
		return new Message(src, tag);
	}

	public static Message getAckMessage(IDAddressPair src) {
		int tag = Tag.ACK.getNumber();
		return new Message(src, tag);
	}

	//
	// for both routing drivers
	//

	//
	// for iterative lookup
	//

	public static Message getIteRouteNoneMessage(IDAddressPair src,
			ID[] target, int numClosestNodes, int numRootCandidates, RoutingContext[] cxt) {
		int tag = Tag.ITE_ROUTE_NONE.getNumber();
		return new Message(src, tag, target, numClosestNodes, numRootCandidates, cxt);
	}

	public static Message getIteRouteInvokeMessage(IDAddressPair src,
			ID[] target, int numClosestNodes, int numRootCandidates, RoutingContext[] cxt,
			CallbackResultFilter filter, int callbackTag, Serializable[][] callbackArgs, IDAddressPair[] lastHop) {
		int tag = Tag.ITE_ROUTE_INVOKE.getNumber();
		return new Message(src, tag, target, numClosestNodes, numRootCandidates, cxt, filter, callbackTag, callbackArgs, lastHop);
	}

	public static Message getIteRouteJoinMessage(IDAddressPair src,
			IDAddressPair joiningNode, int numClosestNodes, int numRootCandidates, RoutingContext[] cxt,
			IDAddressPair[] lastHop) {
		int tag = Tag.ITE_ROUTE_JOIN.getNumber();
		return new Message(src, tag, joiningNode, numClosestNodes, numRootCandidates, cxt, lastHop);
	}

	public static Message getIteAdjustLastHopReq(IDAddressPair src, ID[] target) {
		int tag = Tag.ITE_ADJUST_LAST_HOP_REQ.getNumber();
		return new Message(src, tag, (Serializable)target);
	}

	public static Message getIteAdjustLastHopRep(IDAddressPair src, IDAddressPair[][] adjustedLastHops) {
		int tag = Tag.ITE_ADJUST_LAST_HOP_REP.getNumber();
		return new Message(src, tag, (Serializable)adjustedLastHops);
	}

	public static Message getIteTerminateNoneMessage(IDAddressPair src,
			ID[] target, int numRootCandidates) {
		int tag = Tag.ITE_TERMINATE_NONE.getNumber();
		return new Message(src, tag, target, numRootCandidates);
	}

	public static Message getIteTerminateInvokeMessage(IDAddressPair src,
			ID[] target, int numRootCandidates,
			CallbackResultFilter filter, int callbackTag, Serializable[][] callbackArgs,
			IDAddressPair[] lastHop) {
		int tag = Tag.ITE_TERMINATE_INVOKE.getNumber();
		return new Message(src, tag, target, numRootCandidates,
				filter, callbackTag, callbackArgs, lastHop);
	}

	public static Message getIteTerminateJoinMessage(IDAddressPair src,
			IDAddressPair joiningNode, int numRootCandidates, IDAddressPair[] lastHop) {
		int tag = Tag.ITE_TERMINATE_JOIN.getNumber();
		return new Message(src, tag, joiningNode, numRootCandidates, lastHop);
	}

	public static Message getIteReplyMessage(IDAddressPair src,
			IDAddressPair[][] closestNodes, IDAddressPair[][] rootCandidates,
			Serializable[] callbackResult) {
		int tag = Tag.ITE_REPLY.getNumber();
		return new Message(src, tag, (Serializable)closestNodes, rootCandidates, callbackResult);
	}

	//
	// for recursive lookup
	//

	public static Message getRecRouteNoneMessage(IDAddressPair src,
			ID[] target, int numRootCandidates, RoutingContext[] cxt, IDAddressPair initiator, int ttl, boolean adjustLastHop, RoutingHop[] route) {
		int tag = Tag.REC_ROUTE_NONE.getNumber();
		return new Message(src, tag, null /*black list*/, target, numRootCandidates, cxt, initiator, ttl, adjustLastHop, route);
	}

	public static Message getRecRouteInvokeMessage(IDAddressPair src,
			ID[] target, int numRootCandidates, RoutingContext[] cxt, IDAddressPair initiator, int ttl, boolean adjustLastHop,
			CallbackResultFilter filter, int callbackTag, Serializable[][] callbackArgs,
			RoutingHop[] route) {
		int tag = Tag.REC_ROUTE_INVOKE.getNumber();
		return new Message(src, tag, null /*black list*/, target, numRootCandidates, cxt, initiator, ttl, adjustLastHop,
				filter, callbackTag, callbackArgs,
				route);
	}

	public static Message getRecRouteJoinMessage(IDAddressPair src,
			IDAddressPair joiningNode, int numRootCandidates, RoutingContext[] cxt, int ttl, boolean adjustLastHop, RoutingHop[] route) {
		int tag = Tag.REC_ROUTE_JOIN.getNumber();
		return new Message(src, tag, null /*black list*/, joiningNode, numRootCandidates, cxt, ttl, adjustLastHop, route);
	}

	public static Message getRecTerminateNoneMessage(IDAddressPair src,
			ID[] target, int numRootCandidates, IDAddressPair initiator, int ttl, RoutingHop[] route) {
		int tag = Tag.REC_TERMINATE_NONE.getNumber();
		return new Message(src, tag, null /*black list*/, target, numRootCandidates, initiator, ttl,
				route);
	}

	public static Message getRecTerminateInvokeMessage(IDAddressPair src,
			ID[] target, int numRootCandidates, IDAddressPair initiator, int ttl,
			CallbackResultFilter filter, int callbackTag, Serializable[][] callbackArgs,
			RoutingHop[] route) {
		int tag = Tag.REC_TERMINATE_INVOKE.getNumber();
		return new Message(src, tag, null /*black list*/, target, numRootCandidates, initiator, ttl,
				filter, callbackTag, callbackArgs,
				route);
	}

	public static Message getRecTerminateJoinMessage(IDAddressPair src,
			IDAddressPair joiningNode, int numRootCandidates, int ttl, RoutingHop[] route) {
		int tag = Tag.REC_TERMINATE_JOIN.getNumber();
		return new Message(src, tag, null /*black list*/, joiningNode, numRootCandidates, ttl, route);
	}

	public static Message getRecAckMessage(IDAddressPair src) {
		int tag = Tag.REC_ACK.getNumber();
		return new Message(src, tag);
	}

	public static Message getRecResultMessage(IDAddressPair src,
			boolean succeed, ID[] target, RoutingResult[] routingRes, Serializable[] callbackResult) {
		int tag = Tag.REC_RESULT.getNumber();
		return new Message(src, tag, null /*black list*/, succeed, target, routingRes, callbackResult);
	}

	/**
	 * Set a black list to a Message.
	 * The message has to be one of REC_ROUTE_*.
	 */
	public static void setBlackList(Message msg, IDAddressPair[] blackList) {
		msg.setContents(0, blackList);
	}
}
