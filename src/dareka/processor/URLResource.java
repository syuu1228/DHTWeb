package dareka.processor;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.ProtocolException;
import java.net.Proxy;
import java.net.Socket;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;
import java.util.Map;

import dareka.common.CloseUtil;
import dareka.common.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class URLResource extends Resource {
    public static Logger logger = LoggerFactory.getLogger(URLResource.class);
    /**
     * Border of using FixedLengthStreamingMode.
     * At this time, all transfer with body use FixedLengthStreamingMode,
     * so BUFFERED_POST_MAX is 0. In some environments, it may cause
     * socket error. If it happens, this should have some amount of size.
     * (5MB or and so on. within half of free heap size.)
     */
    private static final int BUFFERED_POST_MAX = 0;//5 * 1024 * 1024;

    private URL url;
    // proxy must let browser know redirection.
    private boolean followRedirects = false;
    private Proxy proxy;
    private long contentLength = -1;
    private boolean canContinue = true;

    public URLResource(String resource) throws IOException {
        url = new URL(resource);

        // Do not remember proxy so that it is enable to change proxy
        // configuration on runtime.
        String proxyHost = System.getProperty("proxyHost");
        int proxyPort = Integer.getInteger("proxyPort").intValue();
        setProxyNoOverride(proxyHost, proxyPort);
    }

    public void setFollowRedirects(boolean followRedirects) {
        this.followRedirects = followRedirects;
    }

    @Override
    public boolean endEnsuredTransferTo(Socket receiver,
            HttpRequestHeader requestHeader, Config config) throws IOException {
        return transferTo(receiver.getInputStream(),
                receiver.getOutputStream(), requestHeader, config);
    }

    public boolean transferTo(InputStream receiverIn, OutputStream receiverOut,
            HttpRequestHeader requestHeaderArg, Config config)
            throws IOException {
        HttpRequestHeader requestHeader;
        if (requestHeaderArg == null) {
            requestHeader =
                    new HttpRequestHeader(HttpHeader.GET + " " + url.toString()
                            + " HTTP/1.1\r\n\r\n");
            requestHeader.setMessageHeader("User-Agent",
                    "Mozilla/4.0 (compatible; MSIE 6.0)");
        } else {
            requestHeader = requestHeaderArg;
        }

        URLConnection con = url.openConnection(proxy);
        prepareForConnect(requestHeader, receiverIn, con);

        con.connect();

        // ensure consuming errorStream for keep-alive.
        // see
        // http://java.sun.com/javase/6/docs/technotes/guides/net/http-keepalive.html
        try {
            sendBodyIfNeccessary(receiverIn, requestHeader, con);
            contentLength = con.getContentLength();

            boolean knownLengthContent = isKnownLengthContent(con);
            boolean clientCanKeepAlive = isClientCanKeepAlive(requestHeader);
            if (!knownLengthContent || !clientCanKeepAlive) {
                canContinue = false;
            }

            HttpResponseHeader responseHeader =
                    getResponseHeader(con, requestHeader);

            execSendingHeaderSequence(receiverOut, responseHeader);

            InputStream in;
            try {
                // On HttpURLConnection, even if FileNotFoundException occurred
                // above, it appears here.
                // The original FileNotFoundException is included as cause.
                in = con.getInputStream();
            } catch (IOException e) {
                logger.debug(e.getMessage());
                // treat error responses in the same way as normal responses.
                // ad hoc treaing for 407
                if (con instanceof HttpURLConnection) {
                    HttpURLConnection hcon = (HttpURLConnection) con;
                    in = hcon.getErrorStream();
                    if (in == null) {
                        return canContinue;
                    }
                } else {
                    throw e;
                }
            }

            // below, assume IOException does not mean error response, so
            // it is not necessary to consume errorStream.
            try {
                execSendingBodySequence(receiverOut, in, contentLength);
            } catch (IOException e) {
            	logger.debug(e.getMessage());
                canContinue = false;
                try {
                    // if the data is too large, give up to cleanup.
                    if (contentLength < 100 * 1024) {
                        consumeInputStream(in);
                    }
                } catch (IOException e1) {
                	logger.debug(e1.getMessage());
                }
            } finally {
                CloseUtil.close(in);
            }
        } catch (IOException e) {
        	logger.debug(e.getMessage());
            canContinue = false;
            consumeErrorStream(con);
        }

        return canContinue;
    }

    /* (非 Javadoc)
     * @see dareka.processor.Resource#doSetMandatoryHeader(dareka.processor.HttpResponseHeader)
     */
    @Override
    protected void doSetMandatoryResponseHeader(
            HttpResponseHeader responseHeader) {
        if (contentLength == -1) {
            responseHeader.removeMessageHeader(HttpHeader.CONTENT_LENGTH);
        } else {
            responseHeader.setContentLength(contentLength);
        }

        if (canContinue) {
            responseHeader.setMessageHeader(HttpHeader.CONNECTION,
                    HttpHeader.CONNECTION_KEEP_ALIVE);

            if (contentLength != -1) {
                responseHeader.setContentLength(contentLength);
            }
        } else {
            responseHeader.setMessageHeader(HttpHeader.CONNECTION,
                    HttpHeader.CONNECTION_CLOSE);
        }
    }

    /**
     * Set proxy. For security reason, this method must be private because it is
     * called from constructor.
     *
     * @param proxyHost
     * @param proxyPort
     */
    private void setProxyNoOverride(String proxyHost, int proxyPort) {
        if (proxyHost == null || proxyHost.equals("")) {
            proxy = Proxy.NO_PROXY;
        } else {
            proxy =
                    new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost,
                            proxyPort));
        }
    }

    // [nl] プロキシを設定
    public void setProxy(String proxyHost, int proxyPort) {
        setProxyNoOverride(proxyHost, proxyPort);
    }

    private boolean isKnownLengthContent(URLConnection con) throws IOException {
        if (contentLength == -1) {
            if (con instanceof HttpURLConnection) {
                HttpURLConnection hcon = (HttpURLConnection) con;
                int responseCode = hcon.getResponseCode();

                switch (responseCode) {
                case 204: // No Content
                case 205: // Reset Content
                case 304: // Not Modified
                    // the length is always 0.
                    return true;
                default:
                    // do nothing
                    break;
                }

                if (HttpHeader.HEAD.equals(hcon.getRequestMethod())) {
                    // the length is always 0.
                    return true;
                }
            }
            return false;
        } else {
            return true;
        }
    }

    private HttpResponseHeader getResponseHeader(URLConnection con,
            HttpRequestHeader requestHeader) throws IOException {
        HttpResponseHeader responseHeader;

        if (con instanceof HttpURLConnection) {
            responseHeader =
                    new HttpResponseHeader(con.getHeaderField(0) + "\r\n\r\n");
            copyResponseHeaderFrom(con, responseHeader);
        } else {
            responseHeader = new HttpResponseHeader("HTTP/1.1 200 OK\r\n\r\n");
            String contentType = con.getHeaderField(HttpHeader.CONTENT_TYPE);
            if (contentType != null) {
                responseHeader.setMessageHeader(HttpHeader.CONTENT_TYPE,
                        contentType);
            }
        }
        responseHeader.removeHopByHopHeaders();
        responseHeader.setVersion(requestHeader.getVersion());

        return responseHeader;
    }

    private void copyResponseHeaderFrom(URLConnection con,
            HttpResponseHeader responseHeader) {
        // The value from URLConnection#getHeaderFields() does not
        // preserve the order of the values in its List<String>. This
        // causes a problem on Set-Cookie. Some servers send multiple
        // Set-Cookie with different cookie. In this case, browsers
        // remember only the last Set-Cookie. Because of this behavior
        // of browsers, the order of headers is significant. It seems
        // that the List<String> has reverse order, but it may depend
        // on implementations.

        for (int i = 0;; i++) {
            String value = con.getHeaderField(i);
            if (value == null) {
                break;
            }

            String key = con.getHeaderFieldKey(i);
            if (key == null) {
                // start line
                continue;
            }

            responseHeader.addMessageHeader(key, value);
        }
    }

    private void prepareForConnect(HttpRequestHeader requestHeader,
            InputStream receiverIn, URLConnection con) throws ProtocolException {
        long requestContentLength = requestHeader.getContentLength();

        prepareConfiguration(con);
        prepareMethod(requestHeader, receiverIn, con, requestContentLength);
        prepareHeaders(requestHeader, con);
    }

    private void prepareConfiguration(URLConnection con) {
        if (con instanceof HttpURLConnection) {
            HttpURLConnection hcon = (HttpURLConnection) con;
            hcon.setInstanceFollowRedirects(followRedirects);
        }
    }

    private void prepareMethod(HttpRequestHeader requestHeader,
            InputStream receiverIn, URLConnection con, long requestContentLength)
            throws ProtocolException {
        if (isShouldPost(requestHeader, receiverIn, requestContentLength)) {
            con.setDoOutput(true);

            if (requestContentLength > BUFFERED_POST_MAX) {
                if (con instanceof HttpURLConnection) {
                    HttpURLConnection hcon = (HttpURLConnection) con;
                    hcon.setFixedLengthStreamingMode((int) requestContentLength);
                }
            }
        } else if (isShouldHead(requestHeader)) {
            if (con instanceof HttpURLConnection) {
                HttpURLConnection hcon = (HttpURLConnection) con;
                hcon.setRequestMethod(HttpHeader.HEAD);
            }
        }
    }

    private boolean isShouldHead(HttpRequestHeader requestHeader) {
        return HttpHeader.HEAD.equals(requestHeader.getMethod());
    }

    private boolean isShouldPost(HttpRequestHeader requestHeader,
            InputStream receiverIn, long requestContentLength) {
        return receiverIn != null
                && (requestContentLength > 0L || HttpHeader.POST.equals(requestHeader.getMethod()));
    }

    private void prepareHeaders(HttpRequestHeader requestHeader,
            URLConnection con) {
        for (Map.Entry<String, List<String>> entry : requestHeader.getMessageHeaders().entrySet()) {
            String key = entry.getKey();
            List<String> values = entry.getValue();
            for (String value : values) {
                // request properties are empty in the initial state
                // of URLConnection, so there is no concern about
                // duplication with default headers.
                con.addRequestProperty(key, value);
            }
        }
    }

    private void sendBodyIfNeccessary(InputStream receiverIn,
            HttpRequestHeader header, URLConnection con) throws IOException {
        if (con.getDoOutput()) {
            OutputStream out = con.getOutputStream();
            try { // ensure out.close()
                HttpUtil.sendBody(out, receiverIn, header.getContentLength());
            } finally {
                CloseUtil.close(out);
            }
        }
    }

    private void consumeErrorStream(URLConnection con) {
        try {
            if (con instanceof HttpURLConnection) {
                HttpURLConnection hcon = (HttpURLConnection) con;
                InputStream es = hcon.getErrorStream();
                if (es != null) {
                    try {
                        consumeInputStream(es);
                    } finally {
                        CloseUtil.close(es);
                    }
                }
            }
        } catch (IOException e) {
        	logger.debug(e.getMessage());
        }
    }

    private void consumeInputStream(InputStream in) throws IOException {
        // in case of finishing this application
        Thread currentThread = Thread.currentThread();

        byte[] buf = new byte[128];
        while (!currentThread.isInterrupted() && in.read(buf) != -1) {
            // discard the input
        }
    }
}
