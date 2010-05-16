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
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ExpiringSet<T> {
	final static Logger logger = Logger.getLogger("util");

	private final Set<T> internalSet =
		Collections.synchronizedSet(new HashSet<T>());
	private final long expiration;

	public ExpiringSet(long expiration) {
		this.expiration = expiration;
	}

	public void add(final T elem) {
		Runnable r = new Runnable() {
			public void run() {
				internalSet.add(elem);

				try {
					Thread.sleep(expiration);
				}
				catch (InterruptedException e) {
					logger.log(Level.INFO, "Thread#sleep interrupted", e);
				}
				finally {
					internalSet.remove(elem);
				}
			}
		};

		Thread t = new Thread(r);
		t.setName("Expirer on " + elem);
		t.setDaemon(true);
		t.start();
	}

	public boolean contains(T elem) {
		return this.internalSet.contains(elem);
	}

	public void clear() {
		this.internalSet.clear();
	}
}
