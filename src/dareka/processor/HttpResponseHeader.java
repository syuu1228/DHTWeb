package dareka.processor;

import java.io.IOException;
import java.io.InputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dareka.common.HttpIOException;

public class HttpResponseHeader extends HttpHeader {
    /**
     * Status-Line
     * 
     * <pre>
     * Status-Line = HTTP-Version SP Status-Code SP Reason-Phrase CRLF
     * </pre>
     */
    private static final Pattern STATUS_LINE_PATTERN =
            Pattern.compile("^(HTTP/1.[01]) (\\d+)(?: (.*))?\r\n");

    private String version;
    private int statusCode;
    private String reason;

    public HttpResponseHeader(InputStream source) throws IOException {
        super(source);
        init();
    }

    public HttpResponseHeader(String source) throws IOException {
        super(source);
        init();
    }

    private void init() throws HttpIOException {
        Matcher m = STATUS_LINE_PATTERN.matcher(getStartLine());
        if (m.find()) {
            version = m.group(1);
            statusCode = Integer.parseInt(m.group(2));
            reason = m.group(3) == null ? "" : m.group(3);
        } else {
            throw new HttpIOException("invalid response:\r\n"
                    + super.toString());
        }
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
        updateStartLine();
    }

    private void updateStartLine() {
        setStartLine(version + " " + statusCode + " " + reason + "\r\n");
    }

    public int getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(int statusCode, String reason) {
        this.statusCode = statusCode;
        this.reason = reason;
        updateStartLine();
    }

    public String getReason() {
        return reason;
    }

}
