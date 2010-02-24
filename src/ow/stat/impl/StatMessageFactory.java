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

package ow.stat.impl;

import java.io.Serializable;

import ow.id.ID;
import ow.id.IDAddressPair;
import ow.messaging.Message;
import ow.messaging.MessagingAddress;
import ow.messaging.Tag;

public class StatMessageFactory {
	public static Message getMessageSentMessage(IDAddressPair src, MessagingAddress msgSrc, MessagingAddress msgDest, int msgTag, int msgLen) {
		int tag = Tag.MESSAGE_SENT.getNumber();
		return new Message(src, tag, msgSrc, msgDest, msgTag, msgLen);
	}

	public static Message getDeleteNodeMessage(IDAddressPair src, MessagingAddress node) {
		int tag = Tag.DELETE_NODE.getNumber();
		return new Message(src, tag, node);
	}

	public static Message getConnectNodesMessage(IDAddressPair src, ID from, ID to, int colorHint) {
		int tag = Tag.CONNECT_NODES.getNumber();
		return new Message(src, tag, from, to, colorHint);
	}

	public static Message getDisconnectNodesMessage(IDAddressPair src, ID from, ID to, int colorHint) {
		int tag = Tag.DISCONNECT_NODES.getNumber();
		return new Message(src, tag, from, to, colorHint);
	}

	public static Message getStatPingMessage(IDAddressPair src) {
		int tag = Tag.STAT_PING.getNumber();
		return new Message(src, tag);
	}

	public static Message getStatAckMessage(IDAddressPair src) {
		int tag = Tag.STAT_ACK.getNumber();
		return new Message(src, tag);
	}

	public static Message getReqNeighbors(IDAddressPair src, int num) {
		int tag = Tag.REQ_NEIGHBORS.getNumber();
		return new Message(src, tag, num);
	}

	public static Message getRepNeighbors(IDAddressPair src, IDAddressPair[] neighbors) {
		int tag = Tag.REP_NEIGHBORS.getNumber();
		return new Message(src, tag, (Serializable)neighbors);
	}
}
