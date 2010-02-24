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

import java.io.IOException;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import ow.id.ID;
import ow.id.IDAddressPair;
import ow.messaging.Message;
import ow.messaging.MessageHandler;
import ow.messaging.MessageReceiver;
import ow.messaging.MessagingAddress;
import ow.messaging.MessagingProvider;
import ow.messaging.Tag;
import ow.stat.MessagingCallback;
import ow.stat.MessagingCallbackExtended;
import ow.stat.MessagingCollector;
import ow.stat.StatConfiguration;

public final class MessagingCollectorImpl implements MessagingCollector {
	private final StatConfiguration config;

	private MessagingProvider provider;
	private MessageReceiver receiver;
	private IDAddressPair selfIDAddress;	// ID is null

	private MessagingCallback callback;
	private final Map<MessagingAddress,ID> idTable = new HashMap<MessagingAddress,ID>();

	public MessagingCollectorImpl(StatConfiguration config) throws Exception {
		this.config = config;

		// initialize messaging subsystem
		this.provider = this.config.deriveMessagingProvider();
		this.receiver = this.config.deriveMessageReceiver(this.provider);
/*
		this.provider = MessagingFactory.getProvider(
				this.config.getMessagingTransport(),
				Signature.getAllAcceptingSignature());

		if (this.config.getSelfAddress() != null) {
			this.provider.setSelfAddress(this.config.getSelfAddress());
		}

		MessagingConfiguration msgConfig = this.provider.getDefaultConfiguration();
		msgConfig.setDoUPnPNATTraversal(config.getDoUPnPNATTraversal());

		this.receiver = this.provider.getReceiver(msgConfig,
				this.config.getSelfPort(), this.config.getSelfPortRange());
		config.setSelfPort(receiver.getPort());	// correct config
*/

		this.selfIDAddress = IDAddressPair.getIDAddressPair(null, this.receiver.getSelfAddress());
	}

	public void start(MessagingCallback callback) throws IOException {
		this.callback = callback;

		// initialize message handlers
		prepareHandlers();
	}

	public void stop() {
		// stop message receiver
		if (this.receiver != null) {
			this.receiver.stop();
		}
	}

	public ID getID(MessagingAddress address) {
		ID id = null;

		synchronized (idTable) {
			id = idTable.get(address);
		}

		return id;
	}

	public MessagingProvider getMessagingProvider() { return this.provider; }
	public MessageReceiver getMessageReceiver() { return this.receiver; }

	private void prepareHandlers() {
		MessageHandler handler;

		handler = new MessageHandler() {
			public Message process(Message msg) {
				Message ret = null;

				Serializable[] contents = msg.getContents();
				int msgTag = msg.getTag();

				if (msgTag == Tag.STAT_PING.getNumber()) {
					ret = StatMessageFactory.getStatAckMessage(selfIDAddress);
				}
				else if (msgTag == Tag.MESSAGE_SENT.getNumber()) {
					MessagingAddress src = (MessagingAddress)contents[0];
					MessagingAddress dest = (MessagingAddress)contents[1];
					int tag = (Integer)contents[2];
					int len = (Integer)contents[3];

					// register the id:address table
					IDAddressPair source = msg.getSource();
					MessagingAddress addr = source.getAddress();
					ID id = source.getID();
					if (id != null) {
						synchronized (idTable) {
							ID oldID = idTable.get(addr);
							if (oldID == null || !oldID.equals(id)) {
								idTable.put(addr, id);
							}
						}
					}

					callback.messageSent(src, dest, tag, len);
				}
				else if (msgTag == Tag.DELETE_NODE.getNumber()) {
					MessagingAddress node = (MessagingAddress)contents[0];

					// invoke callback
					callback.nodeFailed(node);
				}
				else if (callback instanceof MessagingCallbackExtended) {
					MessagingCallbackExtended cb = (MessagingCallbackExtended)callback;

					if (msgTag == Tag.EMPHASIZE_NODE.getNumber()) {
						ID nodeID = (ID)contents[0];

						// invoke callback
						cb.emphasizeNode(nodeID);
					}
					else if (msgTag == Tag.MARK_ID.getNumber()) {
						ID[] ids = (ID[])contents[0];
						int hint = (Integer)contents[1];

						// invoke callback
						for (ID id: ids) cb.markID(id, hint);
					}
					else if (msgTag == Tag.CONNECT_NODES.getNumber()) {
						ID from = (ID)contents[0];
						ID to = (ID)contents[1];
						int colorHint = (Integer)contents[2];

						// invoke callback
						cb.connectNodes(from, to, colorHint);
					}
					else if (msgTag == Tag.DISCONNECT_NODES.getNumber()) {
						ID from = (ID)contents[0];
						ID to = (ID)contents[1];
						int colorHint = (Integer)contents[2];

						// invoke callback
						cb.disconnectNodes(from, to, colorHint);
					}
				}

				return ret;
			}
		};
		this.receiver.addHandler(handler);
	}
}
