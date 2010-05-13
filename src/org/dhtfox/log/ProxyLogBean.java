package org.dhtfox.log;

import java.net.URI;
import java.util.Date;

import org.supercsv.cellprocessor.ParseDate;
import org.supercsv.cellprocessor.ift.CellProcessor;

public class ProxyLogBean {
	public static final CellProcessor[] PROCESSORS = new CellProcessor[] {
			new ParseDate("yy/mm/dd hh:mm:ss"), null, null };
	public static final String[] HEADER = { "date", "uri", "method" };
	Date date;
	String uri, method;

	public ProxyLogBean(Date date, String uri, String method) {
		this.date = date;
		this.uri = uri;
		this.method = method;
	}

	public Date getDate() {
		return date;
	}

	public void setDate(Date date) {
		this.date = date;
	}

	public ProxyLogBean() {
	}

	public ProxyLogBean(URI uri, String method) {
		new ProxyLogBean(new Date(), uri.toString(), method);
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
