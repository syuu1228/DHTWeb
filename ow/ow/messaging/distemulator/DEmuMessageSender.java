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

package ow.messaging.distemulator;

import java.io.IOException;
import java.io.Serializable;
import java.net.InetAddress;
import java.util.logging.Level;
import java.util.logging.Logger;

import ow.id.IDAddressPair;
import ow.messaging.Message;
import ow.messaging.MessageSender;
import ow.messaging.MessagingAddress;
import ow.messaging.MessagingProvider;
import ow.messaging.Tag;
import ow.messaging.emulator.EmuMessageSender;
import ow.messaging.emulator.EmuMessagingAddress;
import ow.messaging.util.MessagingUtility.HostAndPort;
import ow.stat.MessagingReporter;
import ow.tool.emulator.RemoteControlPipeTable;

public final class DEmuMessageSender implements MessageSender {
	private final static Logger logger = Logger.getLogger("messaging");

	private final InetAddress selfInetAddress;
	private final MessagingAddress selfNetAddress;
	private final EmuMessageSender emuSender;
	private final MessageSender netSender;
	private final MessagingProvider netProvider;
	private final RemoteControlPipeTable hostTable;

	private MessagingReporter msgReporter;

	DEmuMessageSender(InetAddress selfInetAddress, MessagingAddress selfNetAddress,
			EmuMessageSender emuSender, MessageSender netSender, MessagingProvider netProvider,
			RemoteControlPipeTable hostTable,
			MessagingReporter msgReporter) {
		this.selfInetAddress = selfInetAddress;
		this.selfNetAddress = selfNetAddress;
		this.emuSender = emuSender;
		this.netSender = netSender;
		this.netProvider = netProvider;
		this.hostTable = hostTable;

		this.msgReporter = msgReporter;
	}

	public void setMessagingReporter(MessagingReporter msgReporter) { this.msgReporter = msgReporter; }

	public void send(MessagingAddress dest, Message msg) throws IOException {
		HostAndPort netDest = this.getEmulatorHostAndPort(dest);

		if (netDest == null) {
			logger.log(Level.WARNING, "Could not find a worker: " + dest);
			return;
		}

		try {
			if (netDest.getHostAddress().equals(selfInetAddress)) {
				// send into emulator itself
				this.emuSender.send(dest, msg);
			}
			else {	// send over network
				// encapsulate
				Message encapsulated = DEmuMessageFactory.getEncapsulatedMessage(
						IDAddressPair.getIDAddressPair(null, this.selfNetAddress), dest, msg, false);

				MessagingAddress netAddress = this.netProvider.getMessagingAddress(
						netDest.getHostName(), netDest.getPort());

				this.netSender.send(netAddress, encapsulated);
			}
		}
		catch (IOException e) {
			// notify statistics collector
			if (this.msgReporter != null) {
				this.msgReporter.notifyStatCollectorOfDeletedNode(msg.getSource(), dest, msg.getTag());
			}

			throw e;
		}

		// notify statistics collector
		if (this.msgReporter != null) {
			// TODO: measures the length of the message.
			this.msgReporter.notifyStatCollectorOfMessageSent(dest, msg, 0);
		}
	}

	public Message sendAndReceive(MessagingAddress dest, Message msg) throws IOException {
		Message ret = null;

		HostAndPort netDest = this.getEmulatorHostAndPort(dest);

		if (netDest == null) {
			logger.log(Level.WARNING, "Could not find a worker: " + dest);
			return null;
		}

		try {
			if (netDest.getHostAddress().equals(selfInetAddress)) {
				// send into emulator itself
				ret = this.emuSender.sendAndReceive(dest, msg);
			}
			else {	// send over network
				// encapsulate
				Message encapsulated = DEmuMessageFactory.getEncapsulatedMessage(
						IDAddressPair.getIDAddressPair(null, this.selfNetAddress), dest, msg, true);

				MessagingAddress netAddress = this.netProvider.getMessagingAddress(
						netDest.getHostName(), netDest.getPort());

				encapsulated = this.netSender.sendAndReceive(netAddress, encapsulated);

				// decapsulate
				if (encapsulated.getTag() == Tag.ENCAPSULATED.getNumber()) {
					Serializable[] contents = encapsulated.getContents();
					ret = (Message)contents[1];
				}
			}
		}
		catch (IOException e) {
			// notify statistics collector
			if (this.msgReporter != null) {
				this.msgReporter.notifyStatCollectorOfDeletedNode(msg.getSource(), dest, msg.getTag());
			}

			throw e;
		}

		// notify statistics collector
		if (this.msgReporter != null) {
			// TODO: measures the length of the message.
			this.msgReporter.notifyStatCollectorOfMessageSent(dest, msg, 0);
		}

		return ret;
	}

	private HostAndPort getEmulatorHostAndPort(MessagingAddress dest) {
		EmuMessagingAddress emuDest = (EmuMessagingAddress)dest;
		int destID = emuDest.getEmuHostID().getHostID();

		return this.hostTable.getWorkerHostAndPort(destID);
	}
}
