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

package ow.messaging.tcp;

import ow.messaging.MessagingConfiguration;

public final class TCPMessagingConfiguration extends MessagingConfiguration {
	public final static int DEFAULT_CONNECTION_POOL_SIZE = 3;

	private int connectionPoolSize = DEFAULT_CONNECTION_POOL_SIZE;
	public int getConnectionPoolSize() { return this.connectionPoolSize; }
	public int setConnectionPoolSize(int size) {
		int old = this.connectionPoolSize;
		this.connectionPoolSize = size;
		return old;
	}
}
