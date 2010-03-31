package dareka.processor;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dareka.common.HttpIOException;

/**
 * HTTP-messageの抽象化。
 *
 */
public class HttpHeader {
	public static Logger logger = LoggerFactory.getLogger(HttpHeader.class);
    public static final String GET = "GET";
    public static final String POST = "POST";
    public static final String HEAD = "HEAD";

    public static final String CONNECTION = "Connection";
    public static final String CONNECTION_CLOSE = "close";
    public static final String CONNECTION_KEEP_ALIVE = "keep-alive";
    public static final String CONTENT_TYPE = "Content-Type";
    public static final String CONTENT_LENGTH = "Content-Length";
    public static final String CONTENT_ENCODING = "Content-Encoding";
    public static final String CONTENT_ENCODING_DEFLATE = "deflate";
    public static final String CONTENT_ENCODING_GZIP = "gzip";

    /**
     * Message Headers.
     *
     * from RFC2616:
     *
     * <pre>
     *        message-header = field-name &quot;:&quot; [ field-value ]
     *        field-name     = token
     *        field-value    = *( field-content | LWS )
     *        field-content  = &lt;the OCTETs making up the field-value
     *                         and consisting of either *TEXT or combinations
     *                         of token, separators, and quoted-string&gt;
     * </pre>
     *
     */
    private static final Pattern MESSAGE_HEADER_PATTERN =
            Pattern.compile("^([^:]+):\\s*(.*)\r?\n");
    private static final Pattern MULTI_TOKEN_SPLIT =
            Pattern.compile("\\s*,\\s*");

    private String startLine;
    private HttpMessageHeaderHolder messageHeaders =
            new HttpMessageHeaderHolder();

    public HttpHeader(InputStream source) throws IOException {
        if (source == null) {
            throw new IllegalArgumentException("source must not be null");
        }

        init(source);
    }

    public HttpHeader(String source) throws IOException {
        if (source == null) {
            throw new IllegalArgumentException("source must not be null");
        }

        init(new ByteArrayInputStream(source.getBytes("ISO-8859-1")));
    }

    private void init(InputStream source) throws IOException, HttpIOException {
        int ch;
        StringBuilder headerString = new StringBuilder(512);
        int lineTopIndex = 0;
        while ((ch = source.read()) != -1) {
            headerString.append((char) ch);

            if (ch != '\n') { // go next read() immediately for performance.
                continue;
            }

            // maybe bad performance..
            if (headerString.toString().endsWith("\r\n\r\n")) {
                break;
            }

            String line = headerString.substring(lineTopIndex);

            if (lineTopIndex == 0) {
                // IE sends additional CRLF after POST request.
                // see http://support.microsoft.com/kb/823099/
                // see http://httpd.apache.org/docs/1.3/misc/known_client_problems.html#trailing-crlf
                if (line.equals("\r\n")) {
                    headerString.setLength(0);
                    continue;
                }

                startLine = line;
            } else {
                Matcher m = MESSAGE_HEADER_PATTERN.matcher(line);
                if (m.find()) {
                    messageHeaders.add(m.group(1), m.group(2));
                } else {
                    logger.warn("invalid header field: " + line);
                }
            }

            lineTopIndex = headerString.length();
        }
/*
        if (lineTopIndex == 0 || ch == -1) {
            throw new HttpIOException("premature end of header: "
                    + headerString);
        }
 * 
 */
    }

    /**
     * ヘッダ全体を文字列として返す。
     *
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        StringBuilder result = new StringBuilder(startLine);
        result.append(messageHeaders.toString());
        result.append("\r\n");

        return result.toString();
    }

    /**
     * Content-Lengthの値を返す。
     *
     * @return Content-Lengthの値。存在しなかった場合は-1。
     */
    public long getContentLength() {
        String value = getMessageHeader(CONTENT_LENGTH);
        if (value == null) {
            return -1;
        } else {
            return Long.parseLong(value);
        }
    }

    /**
     * Content-Lengthの値を変更する。
     *
     * @param contentLength
     */
    public void setContentLength(long contentLength) {
        messageHeaders.put(CONTENT_LENGTH, String.valueOf(contentLength));
    }

    /**
     * メッセージヘッダを返す。keyの大文字小文字は同一視する。
     * 同じkeyの複数のメッセージヘッダがある場合は最後のものを返す。
     *
     * @param key
     * @return メッセージヘッダ。
     */
    public String getMessageHeader(String key) {
        return messageHeaders.get(key);
    }

    /**
     * メッセージヘッダ全体を返す。
     *
     * @return メッセージヘッダ。
     */
    public HttpMessageHeaderHolder getMessageHeaders() {
        return messageHeaders;
    }

    /**
     * メッセージヘッダを設定する。既にあるヘッダを上書きする。
     *
     * @param key
     * @param value
     */
    public void setMessageHeader(String key, String value) {
        messageHeaders.put(key, value);
    }

    /**
     * メッセージヘッダを追加する。
     * @param key
     * @param value
     */
    public void addMessageHeader(String key, String value) {
        messageHeaders.add(key, value);
    }

    /**
     * メッセージヘッダを削除する。
     *
     * @param key
     */
    public void removeMessageHeader(String key) {
        messageHeaders.remove(key);
    }

    protected String getStartLine() {
        return startLine;
    }

    protected void setStartLine(String startLine) {
        this.startLine = startLine;
    }

    /**
     * Hop-by-hop Headersを削除する。RFC2616で定義されているのは以下:
     *
     * <pre>
     *               - Connection (とそれに列挙されているもの)
     *               - Keep-Alive
     *               - Proxy-Authenticate
     *               - Proxy-Authorization
     *               - TE
     *               - Trailer
     *               - Transfer-Encoding
     *               - Upgrade
     * </pre>
     *
     * 標準ではないがProxy-Connectionも。
     */
    public void removeHopByHopHeaders() {
        removeConnectionAndRelated();

        removeMessageHeader("Keep-Alive");
        // ad hoc treaing for 407
        //removeMessageHeader("Proxy-Authenticate");
        //removeMessageHeader("Proxy-Authorization");
        removeMessageHeader("TE");
        removeMessageHeader("Trailer");
        removeMessageHeader("Transfer-Encoding");
        removeMessageHeader("Upgrade");
        removeMessageHeader("Proxy-Connection");
    }

    protected void removeConnectionAndRelated() {
        String connection = getMessageHeader(CONNECTION);
        if (connection == null) {
            return;
        }

        String[] tokens = MULTI_TOKEN_SPLIT.split(connection);

        for (String token : tokens) {
            removeMessageHeader(token);
        }

        removeMessageHeader(CONNECTION);
    }

}
