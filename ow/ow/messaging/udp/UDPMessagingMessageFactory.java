/*
 * Copyright 2008 National Institute of Advanced Industrial Science
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

package ow.messaging.udp;

import ow.id.IDAddressPair;
import ow.messaging.InetMessagingAddress;
import ow.messaging.Message;
import ow.messaging.Tag;

public class UDPMessagingMessageFactory {
	public static Message getPunchHoleReqMessage(IDAddressPair src) {
		int tag = Tag.PUNCH_HOLE_REQ.getNumber();
		return new Message(src, tag);
	}

	public static Message getPunchHoleRepMessage(IDAddressPair src, InetMessagingAddress addr) {
		int tag = Tag.PUNCH_HOLE_REP.getNumber();
		return new Message(src, tag, addr);
	}
}
