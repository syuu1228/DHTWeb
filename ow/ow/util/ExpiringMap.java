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

package ow.util;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ExpiringMap<K,V> {
	final static Logger logger = Logger.getLogger("util");

	private final Map<K,V> internalMap =
		Collections.synchronizedMap(new HashMap<K,V>());
	private final long expiration;

	public ExpiringMap(long expiration) {
		this.expiration = expiration;
	}

	public void put(final K key, final V value) {
		Runnable r = new Runnable() {
			public void run() {
				internalMap.put(key, value);

				try {
					Thread.sleep(expiration);
				}
				catch (InterruptedException e) {
					logger.log(Level.INFO, "Thread#sleep interrupted", e);
				}
				finally {
					synchronized (internalMap) {
						V v = internalMap.get(key);
						if (value.equals(v))
							internalMap.remove(key);
					}
				}
			}
		};

		Thread t = new Thread(r);
		t.setName("Expirer on " + key);
		t.setDaemon(true);
		t.start();
	}

	public V get(K key) {
		return this.internalMap.get(key);
	}

	public void clear() {
		this.internalMap.clear();
	}
}
