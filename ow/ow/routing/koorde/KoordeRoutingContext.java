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

package ow.routing.koorde;

import java.math.BigInteger;

import ow.id.ID;
import ow.routing.RoutingContext;

public class KoordeRoutingContext implements RoutingContext {
	private final int digitBits;
	private ID kshift;
	private ID i;
	private int topBitsOfLastKshift;

	KoordeRoutingContext(ID kshift, ID i, int digitBits) {
		this(kshift, i, 0, digitBits);
	}

	private KoordeRoutingContext(ID kshift, ID i, int topBitsOfLastKshift, int digitBits) {
		this.kshift = kshift;
		this.i = i;
		this.topBitsOfLastKshift = topBitsOfLastKshift;
		this.digitBits = digitBits;
	}

	public ID getKshift() { return this.kshift; }
	public ID getI() { return this.i; }
	public int getTopBitsOfLastKshift() { return this.topBitsOfLastKshift; }

	public RoutingContext next() {
		int idSize = this.kshift.getSize();
		ID nextKshift;
		ID nextI;

		int topBitsOfKshift =
			this.kshift.getBits(idSize * 8 - this.digitBits, this.digitBits);

		// kshift = kshift << 1
		nextKshift = this.kshift.shiftLeft(this.digitBits);

		// i = i . topBit(kshift)
		BigInteger iInteger = this.i.toBigInteger();
		iInteger = iInteger.shiftLeft(this.digitBits).or(BigInteger.valueOf(topBitsOfKshift));
		nextI = ID.getID(iInteger, idSize);

		return new KoordeRoutingContext(nextKshift, nextI, topBitsOfKshift, this.digitBits);
		//this.kshift = nextKshift;
		//this.i = nextI;
		//this.topBitsOfLastKshift = topBitsOfKshift;
	}

	public int hashCode() {
		return this.kshift.hashCode() ^ this.i.hashCode();
	}

	public boolean equals(Object o) {
		if (o instanceof KoordeRoutingContext) {
			KoordeRoutingContext c = (KoordeRoutingContext)o;
			if (this.kshift.equals(c.kshift)
					&& this.i.equals(c.i)) {
				return true;
			}
		}

		return false;
	}

	public String toString() {
		StringBuffer sb = new StringBuffer();
		sb.append("[kshift:").append(this.kshift).append(",i:").append(this.i).append("]");
		return sb.toString();
	}
}
