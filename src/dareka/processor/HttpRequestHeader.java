package dareka.processor;

import java.io.IOException;
import java.io.InputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dareka.common.HttpIOException;

public class HttpRequestHeader extends HttpHeader {
    /**
     * Request-Line
     * 
     * <pre>
     * Request-Line   = Method SP Request-URI SP HTTP-Version CRLF
     * </pre>
     */
    private static final Pattern PROXY_REQUEST_LINE_PATTERN =
            Pattern.compile("^([A-Z]+) ((?:http://)?([^/:]+)(?::(\\d+))?(/\\S*)?) (HTTP/1\\.[01])\r\n");
    private String method;
    private String uri;
    private String host;
    private int port;
    private String path;
    private String version;

    public HttpRequestHeader(InputStream source) throws IOException {
        super(source);
        init();
    }

    public HttpRequestHeader(String source) throws IOException {
        super(source);
        init();
    }

    private void init() throws HttpIOException {
        Matcher m = PROXY_REQUEST_LINE_PATTERN.matcher(getStartLine());
        if (m.find()) {
            method = m.group(1);
            uri = m.group(2);
            host = m.group(3);
            port = m.group(4) == null ? 80 : Integer.parseInt(m.group(4));
            path = m.group(5) == null ? "" : m.group(5);
            version = m.group(6);
        } else {
            throw new HttpIOException("invalid request:\r\n" + super.toString());
        }
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
        updateStartLine();
    }

    public String getURI() {
        return uri;
    }

    public void setURI(String uri) {
        this.uri = uri;
        updateStartLine();
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getPath() {
        return path;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
        updateStartLine();
    }

    public void replaceUriWithPath() {
        if (GET.equals(method) || POST.equals(method)) {
            uri = path;
            updateStartLine();
        }
    }

    private void updateStartLine() {
        setStartLine(method + " " + uri + " " + version + "\r\n");
    }

}
