/*
 * Copyright 2006-2007 National Institute of Advanced Industrial Science
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

package ow.routing.tapestry;

import java.io.Serializable;

import ow.id.ID;
import ow.id.IDAddressPair;
import ow.messaging.Message;
import ow.messaging.Tag;

public class TapestryMessageFactory {
	public static Message getUpdateRoutingTableMessage(IDAddressPair src,
			IDAddressPair[] nodes) {
		int tag = Tag.UPDATE_ROUTING_TABLE.getNumber();
		return new Message(src, tag, (Serializable)nodes);
	}

	public static Message getMulticastJoiningNodeMessage(IDAddressPair src,
			ID prefix, int prefixLength, IDAddressPair joiningNode) {
		int tag = Tag.MULTICAST_JOINING_NODE.getNumber();
		return new Message(src, tag, prefix, prefixLength, joiningNode);
	}

	public static Message getMulticastAckMessage(IDAddressPair src) {
		int tag = Tag.MULTICAST_ACK.getNumber();
		return new Message(src, tag);
	}

	public static Message getNotifyJoiningNodeMessage(IDAddressPair src) {
		int tag = Tag.NOTIFY_JOINING_NODE.getNumber();
		return new Message(src, tag);
	}
}
