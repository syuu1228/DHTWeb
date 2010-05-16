/*
 * Copyright 2008-2009 Kazuyuki Shudo, and contributors.
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

package ow.dht.memcached.impl;

import java.io.Serializable;

import ow.dht.ByteArray;
import ow.dht.memcached.Memcached;
import ow.id.IDAddressPair;
import ow.messaging.Message;
import ow.messaging.Tag;

/**
 * An utility class to create messages for memcached.
 */
public class MemcachedMessageFactory {
	public static Message getPutOnConditionMessage(IDAddressPair src,
			Memcached.PutRequest[] requests,
			long ttl, ByteArray hashedSecret, int numReplica) {
		int tag = Tag.PUT_ON_CONDITION.getNumber();
		return new Message(src, tag, (Serializable)requests, ttl, hashedSecret, numReplica);
	}
}
