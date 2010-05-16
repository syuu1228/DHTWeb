/*
 * Copyright 2006-2007,2009 National Institute of Advanced Industrial Science
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

package ow.messaging.tcp;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Establish an outgoing connection, pool it and return it.
 */
final class ConnectionPool {
	private final static Logger logger = Logger.getLogger("messaging");

	private final int size;
	private Map<SocketAddress,SocketChannel> connectionMap;
	private final Random rnd;

	ConnectionPool(int size) {
		this.size = size;
		if (size > 0) this.connectionMap = new HashMap<SocketAddress,SocketChannel>();
		this.rnd = new Random();
	}

	/**
	 * Look for a Socket connected to dest, if found remove it from the table and return it.
	 * Otherwise connect.
	 * Note that the returned Socket is possible to be already closed.
	 */
	public SocketChannel get(SocketAddress dest) throws IOException {
		if (this.size <= 0) return SocketChannel.open(dest);

		SocketChannel sock = null;
		synchronized (this.connectionMap) {
			sock = this.connectionMap.remove(dest);	// retrieve a Socket
		}

		if (sock != null) {
			logger.log(Level.INFO, "A Socket found in the hash table: ", sock);
			return sock;
		}
		else {
			try {
				sock = SocketChannel.open(dest);
				logger.log(Level.INFO, "A new Socket created: " + dest);
			}
			catch (IOException e) {
				logger.log(Level.INFO, "Could not create a Socket: " + dest);
				throw e;
			}

			return sock;
		}
	}

	public void put(SocketAddress addr, SocketChannel sock) {
		if (this.size <= 0) {
			try {
				sock.close();
			}
			catch (IOException e) { /* ignore */ }

			return;
		}

		SocketChannel existedChannel = null;

		synchronized (this.connectionMap) {
			existedChannel = this.connectionMap.remove(addr);

			// keep table size 
			if (this.connectionMap.size() + 1 >= this.size) {
				logger.log(Level.INFO, "Connection pool is full. Remove an entry.");

				// remove an entry randomly
				//synchronized (this.connectionMap) {
					int removeIdx = rnd.nextInt(this.connectionMap.size());

					SocketAddress removedKey = null;
					for (SocketAddress key: this.connectionMap.keySet()) {
						if (removeIdx == 0) {
							removedKey = key;
							break;
						}
						removeIdx--;
					}

					if (removedKey != null)
						existedChannel = this.connectionMap.remove(removedKey);
				//}
			}

			// put
			this.connectionMap.put(addr, sock);
		}	// synchronized (this.connectionMap)

		// disposes an existing connection
		if (existedChannel != null) {
			try {
				existedChannel.close();
			}
			catch (IOException e) { /* ignore */ }
		}
	}

	public void clear() {
		if (this.size <= 0) return;

		synchronized (connectionMap) {
			for (SocketChannel sock: this.connectionMap.values()) {
				try {
					sock.close();
				}
				catch (IOException e) { /* ignore */ }
			}

			this.connectionMap.clear();
		}
	}
}
