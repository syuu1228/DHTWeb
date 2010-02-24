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

package ow.messaging;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;

public final class Tag {
	//
	// implementation
	//

	private static volatile int lastNumber = 0;
		// this declaration has to be before the protocol declarations (PING, ACK, ...)

	private final int number;
		// in a message, only 1 byte is effective though this member "number" is 4 byte.
	private final String name;
	private boolean toBeReportedToStatCollector;
	private Color color;

	private Tag(String name, Object... opts) {
		this(lastNumber, name, opts);
	}

	private Tag(int i, String name, Object... opts) {
		lastNumber = i;
		this.number = lastNumber++;
		this.name = name;
		this.toBeReportedToStatCollector = true;
		this.color = Color.GRAY;

		// parses optional arguments
		for (Object o: opts) {
			if (o instanceof Boolean) {
				this.toBeReportedToStatCollector = (Boolean)o;
			}
			else if (o instanceof Color) {
				this.color = (Color)o;
			}
		}

		// for Overlay Visualizer
		colorTable.put(this.number, color);

		tagTable.put(this.number, this);
		nameTable.put(this.number, name);
	}

	public int getNumber() { return this.number; }
	public String getName() { return this.name; }
	public Color getColor() { return this.color; }

	//
	// for Overlay Visualizer
	//

	private final static Map<Integer,Color> colorTable = new HashMap<Integer,Color>();

	public static Color getColor(int number) {
		Color ret = colorTable.get(number);
		if (ret == null) {
			ret = Color.GRAY;
		}

		return ret;
	}

	public static boolean toBeReportedToStatCollector(int tag) {
		Tag o = getTagByNumber(tag);

		if (o != null) {
			return o.toBeReportedToStatCollector;
		}

		return true;
	}

	//
	// for debug
	//

	private final static Map<Integer,Tag> tagTable = new HashMap<Integer,Tag>();
	private final static Map<Integer,String> nameTable = new HashMap<Integer,String>();

	public static Tag getTagByNumber(int number) {
		return tagTable.get(number);
	}

	public static String getNameByNumber(int number) {
		String ret = nameTable.get(number);
		if (ret == null) {
			ret = String.valueOf(number);
		}

		return ret;
	}

	//
	// message tags
	//

	// for messaging layer (UDP hole punching)
	public final static Tag PUNCH_HOLE_REQ = new Tag(0, "PUNCH_HOLE_REQ", false);
	public final static Tag PUNCH_HOLE_REP = new Tag("PUNCH_HOLE_REP", false);

	// for routing
	public final static Tag PING = new Tag("PING");
	public final static Tag ACK = new Tag("ACK");

	// iterative routing
	public final static Tag ITE_ROUTE_NONE = new Tag("ROUTE_NONE", Color.BLACK);
	public final static Tag ITE_ROUTE_INVOKE = new Tag("ROUTE_INVOKE", Color.BLACK);
	public final static Tag ITE_ROUTE_JOIN = new Tag("ROUTE_JOIN");
	public final static Tag ITE_ADJUST_LAST_HOP_REQ = new Tag("ADJUST_LAST_HOP_REQ", Color.BLACK);
	public final static Tag ITE_ADJUST_LAST_HOP_REP = new Tag("ADJUST_LAST_HOP_REP", Color.BLACK);
	public final static Tag ITE_TERMINATE_NONE = new Tag("TERMINATE_NONE", Color.BLACK);
	public final static Tag ITE_TERMINATE_INVOKE = new Tag("TERMINATE_INVOKE", Color.BLACK);
	public final static Tag ITE_TERMINATE_JOIN = new Tag("TERMINATE_JOIN");
	public final static Tag ITE_REPLY = new Tag("REPLY");

	// recursive routing
	public final static Tag REC_ROUTE_NONE = new Tag("ROUTE_NONE", Color.BLACK);
	public final static Tag REC_ROUTE_INVOKE = new Tag("ROUTE_INVOKE", Color.BLACK);
	public final static Tag REC_ROUTE_JOIN = new Tag("ROUTE_JOIN");
	public final static Tag REC_TERMINATE_NONE = new Tag("TERMINATE_NONE", Color.BLACK);
	public final static Tag REC_TERMINATE_INVOKE = new Tag("TERMINATE_INVOKE", Color.BLACK);
	public final static Tag REC_TERMINATE_JOIN = new Tag("TERMINATE_JOIN");
	public final static Tag REC_ACK = new Tag("ACK");
	public final static Tag REC_RESULT = new Tag("RESULT");

	// for routing algorithms

	// Linear Walker
	public final static Tag REQ_CONNECT = new Tag("REQ_CONNECT");
	public final static Tag REP_CONNECT = new Tag("REP_CONNECT");
	public final static Tag REQ_SUCCESSOR = new Tag("REQ_SUCCESSOR");
	public final static Tag REP_SUCCESSOR = new Tag("REP_SUCCESSOR");

	// Chord
	public final static Tag UPDATE_FINGER_TABLE = new Tag("UPDATE_FINGER_TABLE");
	public final static Tag ACK_FINGER_TABLE = new Tag("ACK_FINGER_TABLE");

	// Koorde
	public final static Tag REQ_PREDECESSOR = new Tag("REQ_PREDECESSOR");
	public final static Tag REP_PREDECESSOR = new Tag("REP_PREDECESSOR");

	// Pastry and Tapestry
	public final static Tag UPDATE_ROUTING_TABLE = new Tag("UPDATE_ROUTING_TABLE");

	// Tapestry
	public final static Tag MULTICAST_JOINING_NODE = new Tag("MULTICAST_JOINING_NODE");
	public final static Tag MULTICAST_ACK = new Tag("MULTICAST_ACK");
	public final static Tag NOTIFY_JOINING_NODE = new Tag("NOTIFY_JOINING_NODE");

	// Pastry
	public final static Tag REQ_LEAF_SET = new Tag("REQ_LEAF_SET");
	public final static Tag REP_LEAF_SET = new Tag("REP_LEAF_SET");
	public final static Tag REQ_ROUTING_TABLE_ROW = new Tag("REQ_ROUTING_TABLE_ROW");
	public final static Tag REP_ROUTING_TABLE_ROW = new Tag("REP_ROUTING_TABLE_ROW");

	// for DHT
	public final static Tag GET = new Tag("GET");
	public final static Tag PUT = new Tag("PUT");
	public final static Tag REMOVE = new Tag("REMOVE");
	public final static Tag DHT_REPLY = new Tag("DHT_REPLY");
	public final static Tag PUT_VALUEINFO = new Tag("PUT_VALUEINFO");
	public final static Tag REQ_TRANSFER = new Tag("REQ_TRANSFER");
		// REQ_TRANSFER and REP_TRANSFER are for key-value pair transfer

	// for memcached
	public final static Tag PUT_ON_CONDITION = new Tag("PUT_ON_CONDITION");

	// for Mcast
	public final static Tag CONNECT = new Tag("CONNECT");
	public final static Tag ACK_CONNECT = new Tag("ACK_CONNECT");
	public final static Tag NACK_CONNECT = new Tag("NACK_CONNECT");
	public final static Tag DISCONNECT = new Tag("DISCONNECT");
	public final static Tag DISCONNECT_AND_REFUSE = new Tag("DISCONNECT_AND_REFUSE");
	public final static Tag MULTICAST = new Tag("MULTICAST");

	// for tunneling
	public final static Tag ENCAPSULATED = new Tag("ENCAPSULATED", false);
		// for ow.messaging.distemulator

	// for Messaging Statistics Collector
	public final static Tag MESSAGE_SENT = new Tag("MESSAGE_SENT", false);

	// for Overlay Visualizer
	public final static Tag DELETE_NODE = new Tag("DELETE_NODE", false);
	public final static Tag EMPHASIZE_NODE = new Tag("EMPHASIZE_NODE", false);
	public final static Tag MARK_ID = new Tag("MARK_ID", false);
	public final static Tag CONNECT_NODES = new Tag("CONNECT_NODES", false);
	public final static Tag DISCONNECT_NODES = new Tag("DISCONNECT_NODES", false);

	public final static Tag STAT_PING = new Tag("STAT_PING", false);
	public final static Tag STAT_ACK = new Tag("STAT_ACK", false);

	public final static Tag REQ_NEIGHBORS = new Tag("REQ_NEIGHBORS", false);
	public final static Tag REP_NEIGHBORS = new Tag("REP_NEIGHBORS", false);
}
