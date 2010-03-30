package dareka.processor;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpUtil {
    public static Logger logger = LoggerFactory.getLogger(HttpUtil.class);
    private static final int BUF_SIZE = 32 * 1024;

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
            receiverCh.write(bbuf);
        }

        if (contentLength != -1 && len == -1) {
            logger.warn("content may be imcomplete.");
        }
    }
}
