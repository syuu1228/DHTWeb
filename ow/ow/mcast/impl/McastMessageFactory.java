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

package ow.mcast.impl;

import java.io.Serializable;

import ow.id.ID;
import ow.id.IDAddressPair;
import ow.messaging.Message;
import ow.messaging.Tag;
import ow.routing.impl.RoutingDriverMessageFactory;

public class McastMessageFactory extends RoutingDriverMessageFactory {
	public static Message getConnectMessage(IDAddressPair src, ID groupID) {
		int tag = Tag.CONNECT.getNumber();
		return new Message(src, tag, groupID);
	}

	public static Message getAckConnectMessage(IDAddressPair src) {
		int tag = Tag.ACK_CONNECT.getNumber();
		return new Message(src, tag);
	}

	public static Message getNackConnectMessage(IDAddressPair src) {
		int tag = Tag.NACK_CONNECT.getNumber();
		return new Message(src, tag);
	}

	public static Message getDisconnectMessage(IDAddressPair src, ID groupID) {
		int tag = Tag.DISCONNECT.getNumber();
		return new Message(src, tag, groupID);
	}

	public static Message getDisconnectAndRefuseMessage(IDAddressPair src, ID groupID) {
		int tag = Tag.DISCONNECT_AND_REFUSE.getNumber();
		return new Message(src, tag, groupID);
	}

	public static Message getMulticastMessage(IDAddressPair src, ID groupID, int ttl, Serializable payload) {
		int tag = Tag.MULTICAST.getNumber();
		return new Message(src, tag, groupID, ttl, payload);
	}
}
