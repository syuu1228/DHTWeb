/*
 * Copyright 2006 Kazuyuki Shudo, and contributors.
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

import ow.routing.RoutingContext;

/**
 * A triplet of {@link ow.id.ID ID},
 * {@link ow.messaging.MessagingAddress MessagingAddress}
 * and {@link ow.routing.RoutingContext RoutingContext}.
 */
public class IDAddressRoutingContextTriplet extends IDAddressPair {
	private RoutingContext context;

	public IDAddressRoutingContextTriplet(IDAddressPair p, RoutingContext cxt) {
		super(p.getID(), p.getAddress());

		this.context = cxt;
	}

	public RoutingContext getRoutingContext() { return this.context; }
	public RoutingContext setRoutingContext(RoutingContext cxt) {
		RoutingContext old = this.context;
		this.context = cxt;
		return old;
	}

	public IDAddressPair getIDAddressPair() {
		return new IDAddressPair(this.getID(), this.getAddress());
	}

	public int hashCode() {
		int h = super.hashCode();
		if (this.context != null) {
			h ^= this.context.hashCode();
		}

		return h;
	}

	public boolean equals(Object o) {
		if (!(o instanceof IDAddressRoutingContextTriplet))
			return false;

		IDAddressRoutingContextTriplet other = (IDAddressRoutingContextTriplet)o;

		if (!super.equals(other))
			return false;

		if (this.context == null) {
			if (other.context != null)
				return false;
		}
		else {
			if (!this.context.equals(other.context))
				return false;
		}

		return true;
	}

// IDAddressPair#toString used if commented out
//	public String toString() {
//		StringBuilder sb = new StringBuilder();
//
//		sb.append(super.toString());	// <ID>:<address>
//		sb.append(":");
//		sb.append(this.context == null ? "(null)" : this.context.toString());
//
//		return sb.toString();
//	}
}
