/*
 * NicoCache License

Copyright (c) 2007, ASR
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

    * Redistributions in source and binary form must reproduce the
      above copyright notice, this list of conditions and the
      following disclaimer in the documentation and/or other materials
      provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
``AS IS'' AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
HOLDERS AND CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/
package org.dhtweb;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.WritableByteChannel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpUtil {
	public static Logger logger = LoggerFactory.getLogger(HttpUtil.class);
    private static final int BUF_SIZE = 32 * 1024;

    private HttpUtil() {
        // avoid instantiation
    }

	public static void sendBodyToNull(InputStream in, int contentLength) throws IOException {
		sendBodyOnChannel(null, Channels.newChannel(in), contentLength);
	}

    public static void sendBody(Socket receiver, Socket sender,
            long contentLength) throws IOException {
        SocketChannel senderCh = sender.getChannel();
        SocketChannel receiverCh = receiver.getChannel();

        sendBodyOnChannel(receiverCh, senderCh, contentLength);
    }

    public static void sendBody(OutputStream out, InputStream in,
            long contentLength) throws IOException {
        sendBodyOnChannel(Channels.newChannel(out), Channels.newChannel(in),
                contentLength);

    }

    private static void sendBodyOnChannel(WritableByteChannel receiverCh,
            ReadableByteChannel senderCh, long contentLength)
            throws IOException {
        long maxLength = contentLength == -1 ? Long.MAX_VALUE : contentLength;

        ByteBuffer bbuf = ByteBuffer.allocate(BUF_SIZE);
        int len = 0;
        for (long currentLength = 0; currentLength < maxLength; currentLength +=
                len) {
            bbuf.clear();
            long remain = maxLength - currentLength;
            if (remain < bbuf.limit()) {
                bbuf.limit((int) remain);
            }

            len = senderCh.read(bbuf);
            if (len == -1) {
                break;
            }

            bbuf.flip();
            if (receiverCh != null)
            	receiverCh.write(bbuf);
        }

        if (contentLength != -1 && len == -1) {
            logger.warn("content may be imcomplete.");
        }
    }
}
