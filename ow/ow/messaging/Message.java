/*
 * Copyright 2006-2008 National Institute of Advanced Industrial Science
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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import ow.id.IDAddressPair;

public class Message implements Serializable, Cloneable {
	private final static Logger logger = Logger.getLogger("messaging");

	public final static boolean GZIP_MESSAGE = true;

	private byte[] signature;
	private final int tag;
	private IDAddressPair src;
	private final Serializable[] contents;	// up to 255 objects due to protocol limitation

	/**
	 * Create an instance of Message class with a given contents.
	 *
	 * @param contents data to be packed.
	 */
	public Message(IDAddressPair src, int tag, Serializable... contents) {
		this(null, src, tag, contents);
	}

	private Message(byte[] signature, IDAddressPair src, int tag, Serializable... contents) {
		this.signature = signature;
		this.src = src;
		this.tag = tag;

		// copy contents
		this.contents = new Serializable[contents.length];
		System.arraycopy(contents, 0, this.contents, 0, contents.length);
	}

	protected Message() {	// for subclasses
		this.tag = -1;
		this.contents = null;
	}

	/** Return the source address. */
	public IDAddressPair getSource() { return this.src; }

	/** Set the source address. */
	public IDAddressPair setSource(IDAddressPair src) {
		IDAddressPair old = this.src;
		this.src = src;
		return old;
	} 

	public byte[] setSignature(byte[] sig) {
		byte[] old = this.signature;
		this.signature = sig;
		return old;
	}

	/**
	 * Returns the signature.
	 */
	public byte[] getSignature() {
		return this.signature;
	}

	/**
	 * Returns the tag.
	 */
	public int getTag() {
		return this.tag;
	}

	/**
	 * Returns the contents.
	 */
	public Serializable[] getContents() {
		return this.contents;
	}

	public void setContents(int i, Serializable c) {
		this.contents[i] = c;
	}

	public String toString() {
		StringBuilder sb = new StringBuilder();

		sb.append("Message {src:");
		sb.append(this.src);
		sb.append(",tag:");
		sb.append(Tag.getNameByNumber(this.tag));
		sb.append(",#contents:");
		sb.append(this.contents.length);
		sb.append("}");

		return sb.toString();
	}

	/**
	 * Write this Message into a byte stream.
	 * This is an utility method implementing the wire protocol.
	 */
	public static ByteBuffer encode(ByteChannel out, Message msg) throws IOException {
		logger.log(Level.INFO, "tag:" + Tag.getNameByNumber(msg.tag) + " # of contents:" + msg.contents.length);

		ByteBuffer buf = encode(msg);

		try {
			synchronized (out) {
				do {
					out.write(buf);
				} while (buf.hasRemaining());
			}
		}
		catch (IOException e) {
			logger.log(Level.WARNING, "Could not write a message.");
			throw e;
		}

		buf.rewind();

		return buf;
	}

	/**
	 * Convert this Message to a ByteBuffer.
	 */
	public static ByteBuffer encode(Message msg) {
		// serializes src and contents
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		OutputStream os = bos;

		try {
			if (GZIP_MESSAGE) {
				os = new GZIPOutputStream(os);
			}

			ObjectOutputStream oos = new ObjectOutputStream(os);
			oos.writeObject(msg.src);
			for (Serializable c: msg.contents) {
				oos.writeObject(c);
			}
			oos.flush();
			oos.close();
		}
		catch (IOException e) {
			// NOTREACHED
			logger.log(Level.SEVERE,
					"Serialization failed: " + Tag.getNameByNumber(msg.getTag()));
		}

		byte[] srcAndContents = bos.toByteArray();
		int signatureLen = Signature.getSignatureLength();

		// pack all elements
		ByteBuffer buf = ByteBuffer.allocate(
				 signatureLen + 6 + srcAndContents.length);
		if (msg.signature != null) {
			buf.put(msg.signature, 0, signatureLen);
		}
		else {
			buf.put(new byte[signatureLen]);
		}
		buf.put((byte)msg.tag);
		buf.putInt(srcAndContents.length);
		buf.put((byte)msg.contents.length);
		buf.put(srcAndContents);

		buf.rewind();

		return buf;
	}

	/**
	 * Read a Message from the given input stream.
	 */
	public static Message decode(ByteChannel in) throws IOException {
		// read header
		int signatureLen = Signature.getSignatureLength();

		int toBeRead = signatureLen + 6;
		ByteBuffer buf = ByteBuffer.allocate(toBeRead);
		readIntoBuffer(in, buf);

		byte[] signature = new byte[signatureLen];
		buf.get(signature, 0, signatureLen);

		int tag = buf.get() & 0xff;
		int len = buf.getInt();
		int argCount = buf.get() & 0xff;

		// read source and content
		buf = ByteBuffer.allocate(len);
		readIntoBuffer(in, buf);

		byte[] srcAndContent = new byte[len];
		buf.get(srcAndContent);

		InputStream is = new ByteArrayInputStream(srcAndContent);
		if (GZIP_MESSAGE) {
			is = new GZIPInputStream(is);
		}
		ObjectInputStream ois = new ObjectInputStream(is);

		IDAddressPair src = null;
		Serializable[] contents = new Serializable[argCount];
		try {
			src = (IDAddressPair)ois.readObject();
			for (int i = 0; i < argCount; i++) {
				contents[i] = (Serializable)ois.readObject();
			}
		}
		catch (ClassNotFoundException e) {
			// NOTREACHED
			logger.log(Level.SEVERE, "Class not found: " + e);
			throw new IOException("Class not found: " + e);
		}

		ois.close();

		return new Message(signature, src, tag, contents);
	}

	private static void readIntoBuffer(ByteChannel in, ByteBuffer buf)
			throws IOException {
		int len = buf.capacity();

		try {
			do {
				int r = in.read(buf);
				if (r < 0) {
					logger.log(Level.INFO, "Reached end-of-stream.");
					throw new IOException("End-of-stream.");
				}
				len -= r;
			} while (len > 0);

			buf.rewind();
		}
		catch (IOException e) {	// catch just for logging
			logger.log(Level.WARNING, "Could not read a message.");
			throw e;
		}
	}

	/**
	 * Convert a ByteBuffer to a Message.
	 */
	public static Message decode(ByteBuffer buf) throws IOException {
		buf.mark();

		int signatureLen = Signature.getSignatureLength();
		byte[] signature = new byte[signatureLen];
		buf.get(signature, 0, signatureLen);

		int tag = buf.get() & 0xff;
		int len = buf.getInt();
		int argCount = buf.get() & 0xff;

		byte[] srcAndContent = new byte[len];
		buf.get(srcAndContent);

		buf.reset();

		IDAddressPair src = null;
		Serializable[] contents = new Serializable[argCount];

		InputStream is = new ByteArrayInputStream(srcAndContent);
		try {
			if (GZIP_MESSAGE) {
				is = new GZIPInputStream(is);
			}
			ObjectInputStream ois = new ObjectInputStream(is);

			src = (IDAddressPair)ois.readObject();
			for (int i = 0; i < argCount; i++) {
				contents[i] = (Serializable)ois.readObject();
			}

			ois.close();
		}
		catch (IOException e) {
			// NOTREACHED
			logger.log(Level.SEVERE, "An IOException thrown.", e);
			throw e;
		}
		catch (ClassNotFoundException e) {
			// NOTREACHED
			logger.log(Level.SEVERE, "Class not found.", e);
			throw new IOException("Class not found: " + e);
		}

		return new Message(signature, src, tag, contents);
	}

	/**
	 * Convert a byte array to a Message.
	 */
	public static Message decodeFromByteArray(byte[] data) throws IOException {
		ByteBuffer buf = ByteBuffer.allocate(data.length);
		buf.put(data);

		buf.rewind();

		return decode(buf);
	}
}
