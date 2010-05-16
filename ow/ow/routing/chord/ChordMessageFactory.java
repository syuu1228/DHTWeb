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

package ow.routing.chord;

import ow.id.IDAddressPair;
import ow.messaging.Message;
import ow.messaging.Tag;

/**
 * An utility class to create a message for Chord.
 */
public class ChordMessageFactory {
	public static Message getUpdateFingerTableMessage(IDAddressPair src,
			int largestIndex) {
		int tag = Tag.UPDATE_FINGER_TABLE.getNumber();
		return new Message(src, tag, largestIndex);
	}

	public static Message getAckFingerTableMessage(IDAddressPair src) {
		int tag = Tag.ACK_FINGER_TABLE.getNumber();
		return new Message(src, tag);
	}
}
