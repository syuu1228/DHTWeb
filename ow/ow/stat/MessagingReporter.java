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

package ow.stat;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import ow.id.ID;
import ow.id.IDAddressPair;
import ow.messaging.Message;
import ow.messaging.MessageSender;
import ow.messaging.MessagingAddress;
import ow.messaging.MessagingProvider;
import ow.messaging.Tag;
import ow.stat.impl.StatMessageFactory;
import ow.tool.util.vizframework.VisualizerMessageFactory;

public final class MessagingReporter {
	private final static Logger logger = Logger.getLogger("statcollector");

	private final StatConfiguration config;
	private final MessagingProvider provider;
	private final MessageSender sender;

	private final int pingFreq;
	private int notificationCount;
	private int failureCount;

	MessagingReporter(StatConfiguration config,
			MessagingProvider provider, MessageSender sender) {
		this.config = config;
		this.provider = provider;
		this.sender = sender;

		this.pingFreq = this.config.getPingFrequency();
		this.notificationCount = 0;
		this.failureCount = 0;
	}

	//
	// for Messaging Services
	//

	public void notifyStatCollectorOfMessageSent(MessagingAddress dest, Message msg, int msgLen) {
		MessagingAddress statCollectorAddress = this.provider.getMessagingCollectorAddress();
		if (statCollectorAddress == null) return;

		int tag = msg.getTag();
		if (!Tag.toBeReportedToStatCollector(tag)) return;

		IDAddressPair self = msg.getSource();
		Message notifyMsg = StatMessageFactory.getMessageSentMessage(self, self.getAddress(), dest, tag, msgLen);
		try {
			this.sender.send(statCollectorAddress, notifyMsg);
		}
		catch (IOException e) {
			logger.log(Level.WARNING, "Failed to send a MESSGAGE_SENT message.", e);
			if (this.failInReporting()) return;
		}

		this.confirmAlive(statCollectorAddress, self);
	}

	public void notifyStatCollectorOfDeletedNode(IDAddressPair self,
			MessagingAddress failedNode, int tag) {
		MessagingAddress statCollectorAddress = this.provider.getMessagingCollectorAddress();
		if (statCollectorAddress == null) return;

		if (!Tag.toBeReportedToStatCollector(tag)) return;

		Message notifyMsg = StatMessageFactory.getDeleteNodeMessage(self, failedNode);
		try {
			this.sender.send(statCollectorAddress, notifyMsg);
		}
		catch (IOException e) {
			logger.log(Level.WARNING, "Failed to send a DELETE message.", e);
			if (this.failInReporting()) return;
		}

		this.confirmAlive(statCollectorAddress, self);
	}

	//
	// for Routing Drivers
	//

	public void notifyStatCollectorOfEmphasizeNode(IDAddressPair self, ID nodeID) {
		MessagingAddress statCollectorAddress = this.provider.getMessagingCollectorAddress();
		if (statCollectorAddress == null) return;

		Message msg = VisualizerMessageFactory.getEmphasizeNodeMessage(self, nodeID);
		try {
			this.sender.send(statCollectorAddress, msg);
		}
		catch (IOException e) {
			logger.log(Level.WARNING, "Failed to send a EMPHASIZE_NODE message.", e);
			if (this.failInReporting()) return;
		}

		this.confirmAlive(statCollectorAddress, self);
	}

	public void notifyStatCollectorOfMarkedID(IDAddressPair self, ID[] ids, int hint) {
		MessagingAddress statCollectorAddress = this.provider.getMessagingCollectorAddress();
		if (statCollectorAddress == null) return;

		Message msg = VisualizerMessageFactory.getMarkIDMessage(self, ids, hint);
		try {
			this.sender.send(statCollectorAddress, msg);
		}
		catch (IOException e) {
			logger.log(Level.WARNING, "Failed to send a MARK_ID message.", e);
			if (this.failInReporting()) return;
		}

		this.confirmAlive(statCollectorAddress, self);
	}

	//
	// for Mcast Service
	//

	public void notifyStatCollectorOfConnectNodes(IDAddressPair self,
			ID child, ID parent, int colorHint) {
		MessagingAddress statCollectorAddress = this.provider.getMessagingCollectorAddress();
		if (statCollectorAddress == null) return;

		Message msg = StatMessageFactory.getConnectNodesMessage(self,
				child, parent, colorHint);
		try {
			this.sender.send(statCollectorAddress, msg);
		}
		catch (IOException e) {
			logger.log(Level.WARNING, "Failed to send a CONNECT_NODES message.", e);
			if (this.failInReporting()) return;
		}

		this.confirmAlive(statCollectorAddress, self);
	}

	public void notifyStatCollectorOfDisconnectNodes(IDAddressPair self,
			ID child, ID parent, int colorHint) {
		MessagingAddress statCollectorAddress = this.provider.getMessagingCollectorAddress();
		if (statCollectorAddress == null) return;

		Message msg = StatMessageFactory.getDisconnectNodesMessage(self,
				child, parent, colorHint);
		try {
			this.sender.send(statCollectorAddress, msg);
		}
		catch (IOException e) {
			logger.log(Level.WARNING, "Failed to send a DISCONNECT_NODES message.", e);
			if (this.failInReporting()) return;
		}

		this.confirmAlive(statCollectorAddress, self);
	}

	/**
	 * Confirms whether the StatCollector is alive or not.
	 */
	private void confirmAlive(MessagingAddress statCollectorAddress, IDAddressPair self) {
		if (this.provider.isReliable()
				|| ++this.notificationCount % this.pingFreq != 0) return;
		// multiple increments can be in race condition w/o synchronization, but it's OK here.

		// ping
		Message pingMessage = StatMessageFactory.getStatPingMessage(self);
		Message ackMessage = null;

		try {
			ackMessage = this.sender.sendAndReceive(statCollectorAddress, pingMessage);

			if (ackMessage.getTag() == Tag.STAT_ACK.getNumber()) {
				//synchronized (this) {
					this.failureCount = 0;
				//}
				return;
			}
		}
		catch (IOException e) {
			logger.log(Level.WARNING, "STAT_PING failed: " + statCollectorAddress);
		}
		catch (NullPointerException e) {
			logger.log(Level.WARNING, "A null message returned. The target is not a stat collector?");
		}

		this.failInReporting();
	}

	private boolean failInReporting() {
		// check whether to forget or not
		boolean toForget = false;
		//synchronized (this) {
			if (++this.failureCount >= this.config.getNumOfFailuresBeforeForgetCollector()) {
				toForget = true;
				this.failureCount = 0;
			}
		//}

		// forget
		if (toForget) {
			logger.log(Level.WARNING, "Forget a stat collector.");

			this.provider.setMessagingCollectorAddress(null);
		}

		return toForget;
	}
}
