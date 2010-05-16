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

package ow.messaging.emulator;

import ow.messaging.MessagingAddress;

public final class EmuMessagingAddress implements MessagingAddress {
	private final EmuHostID host;
	private final int port;
	private final int hashCode;

	EmuMessagingAddress(EmuHostID host, int port) {
		this.host = host;
		this.port = port;
		this.hashCode = this.host.hashCode() ^ this.port;
	}

	public String getHostAddress() { return this.host.toString(); }
	public String getHostname() { return this.host.toString(); }
	public String getHostnameOrHostAddress() { return this.host.toString(); }

	public int getPort() { return this.port; }

	public EmuHostID getEmuHostID() { return this.host; }

	public int hashCode() { return this.hashCode; }

	public boolean equals(Object o) {
		if (o instanceof EmuMessagingAddress) {
			EmuMessagingAddress other = (EmuMessagingAddress)o;
			if (this.host.equals(other.host) && this.port == other.port) {
				return true;
			}
		}

		return false;
	}

	public String toString() {
		return this.toString(0);
	}

	public String toString(int verboseLevel) {
		StringBuilder sb = new StringBuilder();

		sb.append(this.host.toString());
		if (verboseLevel >= 0) {
			sb.append(":");
			sb.append(this.port);
		}

		return sb.toString();
	}
}
