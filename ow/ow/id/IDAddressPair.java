/*
 * Copyright 2006-2009 National Institute of Advanced Industrial Science
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

package ow.id;

import java.io.Serializable;

import ow.messaging.MessagingAddress;

/**
 * A pair of {@link ow.id.ID ID} and {@link ow.messaging.MessagingAddress MessagingAddress}.
 */
public class IDAddressPair implements Serializable {
//	private final static Map<IDAddressPair,IDAddressPair> canonicalizingMap =
//		new WeakHashMap<IDAddressPair,IDAddressPair>();

	private ID id;
	private MessagingAddress addr;	// can be null

	public static IDAddressPair getIDAddressPair(ID id, MessagingAddress address) {
		return canonicalize(new IDAddressPair(id, address));
	}

	public static IDAddressPair getIDAddressPair(int idSizeInByte, MessagingAddress address) {
		return canonicalize(new IDAddressPair(idSizeInByte, address));
	}

	protected IDAddressPair(ID id, MessagingAddress address) {
		this.addr = address;
		this.id = id;
	}

	private IDAddressPair(int idSizeInByte, MessagingAddress address) {
		this.addr = address;
		this.id = calculateIDBasedOnHashcodeOfAddress(idSizeInByte);
	}

	private ID calculateIDBasedOnHashcodeOfAddress(int idSizeInByte) {
		if (this.addr != null)
			return ID.getHashcodeBasedID(this.addr, idSizeInByte);
		else
			return null;
	}

	private static IDAddressPair canonicalize(IDAddressPair obj) {
		return obj;

//		if (obj.id == null) return obj;	// allows an IDAddressPair with null ID.
//
//		IDAddressPair ret;
//		synchronized (canonicalizingMap) {
//			ret = canonicalizingMap.get(obj);
//			if (ret == null) { canonicalizingMap.put(obj, obj); ret = obj; }
//		}
//		return ret;
	}

	public ID getID() { return this.id; }
	public ID setID(ID id) {
		ID old = this.id;

//		synchronized (canonicalizingMap) {
//			if (this.id != null) canonicalizingMap.remove(this);
			this.id = id;
//			canonicalizingMap.put(this, this);
//		}

		return old;
	}

	public MessagingAddress getAddress() { return this.addr; }
	public MessagingAddress setAddressAndRecalculateID(MessagingAddress addr) {
		MessagingAddress old = this.addr;

//		synchronized (canonicalizingMap) {
//			if (this.id != null) canonicalizingMap.remove(this);
			this.addr = addr;
			this.id = calculateIDBasedOnHashcodeOfAddress(this.id.getSize());
//			canonicalizingMap.put(this, this);
//		}

		return old;
	}

	public IDAddressPair getIDAddressPair() { return this; }	// overridden by IDAddressRoutingContext

	public int hashCode() {
		if (this.id != null)
			return this.id.hashCode()/* ^ addr.hashCode()*/;
		else if (this.addr != null)
			return this.addr.hashCode();
		else
			return 0;
	}

	public boolean equals(Object o) {
		if (o == null) return false;

		IDAddressPair other;
		try {
			other = (IDAddressPair)o;
		}
		catch (ClassCastException e) { return false; }

		if (other == null)
			return false;

		if (this.id != null)
			return this.id.equals(other.id);	// ignore MessagingAddress

		if (other.id != null)
			return false;

		// compare addr
		if (this.addr != null) {
			if (other.addr != null)
				if (!this.addr.equals(other.addr))
					return false;
		}
		else {
			if (other.addr != null)
				return false;
		}

		return true;
	}

	public String toString() {
		return this.toString(0);
	}

	public String toString(int verboseLevel) {
		StringBuilder sb = new StringBuilder();

		// <ID>:<address>
		sb.append(this.id == null ? "(null)" : this.id.toString(verboseLevel));
		sb.append(":");
		sb.append(this.addr == null ? "(null)" : this.addr.toString(verboseLevel));

		return sb.toString();
	}
}
