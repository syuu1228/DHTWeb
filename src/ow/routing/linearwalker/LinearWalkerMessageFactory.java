/*
 * Copyright 2006 Kazuyuki Shudo, and contributors.
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

import ow.id.IDAddressPair;
import ow.messaging.Message;
import ow.messaging.Tag;

/**
 * An utility class to create a message for Linear Walker.
 */
public class LinearWalkerMessageFactory {
	public static Message getReqConnectMessage(IDAddressPair src, boolean forceSuccToSetPred) {
		int tag = Tag.REQ_CONNECT.getNumber();
		return new Message(src, tag, forceSuccToSetPred);
	}

	public static Message getRepConnectMessage(IDAddressPair src,
			IDAddressPair lastPredecessor, IDAddressPair[] successors) {
		int tag = Tag.REP_CONNECT.getNumber();
		return new Message(src, tag, lastPredecessor, successors);
	}

	public static Message getReqSuccessorMessage(IDAddressPair src) {
		int tag = Tag.REQ_SUCCESSOR.getNumber();
		return new Message(src, tag);
	}

	public static Message getRepSuccessorMessage(IDAddressPair src,
			IDAddressPair successor) {
		int tag = Tag.REP_SUCCESSOR.getNumber();
		return new Message(src, tag, successor);
	}
}
