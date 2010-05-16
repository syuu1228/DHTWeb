/*
 * Copyright 2006,2008 National Institute of Advanced Industrial Science
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

package ow.tool.util.vizframework;

import ow.id.ID;
import ow.id.IDAddressPair;
import ow.messaging.Message;
import ow.messaging.Tag;

public class VisualizerMessageFactory {
	public static Message getEmphasizeNodeMessage(IDAddressPair src, ID nodeID) {
		int tag = Tag.EMPHASIZE_NODE.getNumber();
		return new Message(src, tag, nodeID);
	}

	public static Message getMarkIDMessage(IDAddressPair src, ID[] ids, int hint) {
		int tag = Tag.MARK_ID.getNumber();
		return new Message(src, tag, ids, hint);
	}
}
