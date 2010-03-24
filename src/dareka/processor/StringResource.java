package dareka.processor;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.Socket;
import java.util.List;
import java.util.Map;

import dareka.common.Config;

public class StringResource extends Resource {
    private String statusline = "HTTP/1.1 200 OK";
    byte[] contentAsBytes;

    private boolean clientCanKeepAlive;

    public StringResource(String resource) {
        contentAsBytes = getBytesAsUtf8(resource);
    }

    public StringResource(byte[] resource) {
        contentAsBytes = resource;
    }

    public StringResource(int status, String resource) {
        switch (status) {
        case 302:
            statusline = "HTTP/1.1 302 Found\r\nLocation:" + resource;
            contentAsBytes = new byte[0]; // represents it's not forgotten.
            return;
        case 304:
            statusline = "HTTP/1.1 304 Not Modified";
            break;
        case 404:
            statusline = "HTTP/1.1 404 Not Found";
            break;
        default:
            throw new IllegalArgumentException("unexpected status code: "
                    + status);
        }
        contentAsBytes = getBytesAsUtf8(resource);
    }

    private byte[] getBytesAsUtf8(String str) {
        try {
            return str.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            // never happen
            throw new IllegalStateException("cannot use UTF-8");
        }
    }

    public void addNoCacheResponseHeaders() {
        setResponseHeader("Expires", "Thu, 01 Dec 1994 16:00:00 GMT");
        setResponseHeader("Cache-Control", "no-store");
        setResponseHeader("Pragma", "no-cache");
    }

    public void copyResponseHeaders(HttpResponseHeader responseHeader) {
        for (Map.Entry<String, List<String>> entry : responseHeader.getMessageHeaders().entrySet()) {
            String key = entry.getKey();
            List<String> values = entry.getValue();
            for (String value : values) {
                addResponseHeader(key, value);
            }
        }
    }

    /* (非 Javadoc)
     * @see dareka.processor.Resource#doSetMandatoryHeader(dareka.processor.HttpResponseHeader)
     */
    @Override
    protected void doSetMandatoryResponseHeader(
            HttpResponseHeader responseHeader) {
        responseHeader.setContentLength(contentAsBytes.length);

        if (clientCanKeepAlive) {
            responseHeader.setMessageHeader(HttpHeader.CONNECTION,
                    HttpHeader.CONNECTION_KEEP_ALIVE);
        } else {
            responseHeader.setMessageHeader(HttpHeader.CONNECTION,
                    HttpHeader.CONNECTION_CLOSE);
        }
    }

    @Override
    public boolean endEnsuredTransferTo(Socket receiver,
            HttpRequestHeader requestHeader, Config config) throws IOException {
        clientCanKeepAlive = isClientCanKeepAlive(requestHeader);
        HttpResponseHeader responseHeader =
                new HttpResponseHeader(statusline + "\r\n\r\n");

        execSendingHeaderSequence(receiver.getOutputStream(), responseHeader);

        execSendingBodySequence(receiver.getOutputStream(),
                new ByteArrayInputStream(contentAsBytes),
                responseHeader.getContentLength());

        return clientCanKeepAlive;
    }

}
