package org.dhtfox.log;

import java.net.URI;
import java.util.Date;

import org.supercsv.cellprocessor.ParseBool;
import org.supercsv.cellprocessor.ParseLong;
import org.supercsv.cellprocessor.ift.CellProcessor;

public class AccessLogBean {
	public static final CellProcessor[] PROCESSORS = new CellProcessor[] {
			null, null, null, new ParseLong(), new ParseBool() };
	public static final String[] HEADER = { "date", "uri", "method", "latency", "result" };
	String date, uri, method;
	long latency;
	boolean result;

	public AccessLogBean(String date, String uri, String method, long latency, boolean result) {
		this.date = date;
		this.uri = uri;
		this.method = method;
		this.latency = latency;
		this.result = result;
	}

	public long getLatency() {
		return latency;
	}

	public void setLatency(long latency) {
		this.latency = latency;
	}

	public String getDate() {
		return date;
	}

	public void setDate(String date) {
		this.date = date;
	}

	public AccessLogBean() {
	}

	public boolean isResult() {
		return result;
	}

	public void setResult(boolean result) {
		this.result = result;
	}

	public String getUri() {
		return uri;
	}

	public void setUri(String uri) {
		this.uri = uri;
	}

	public String getMethod() {
		return method;
	}

	public void setMethod(String method) {
		this.method = method;
	}
}
