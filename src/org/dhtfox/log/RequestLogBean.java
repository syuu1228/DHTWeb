package org.dhtfox.log;

import java.net.URI;
import java.util.Date;

import org.supercsv.cellprocessor.ParseDate;
import org.supercsv.cellprocessor.ift.CellProcessor;

public class RequestLogBean {
	public static final CellProcessor[] PROCESSORS = new CellProcessor[] {
			new ParseDate("yy/mm/dd hh:mm:ss"), null, null };
	public static final String[] HEADER = { "date", "uri", "method" };
	Date date;
	String uri;

	public RequestLogBean(Date date, String uri) {
		this.date = date;
		this.uri = uri;
	}

	public Date getDate() {
		return date;
	}

	public void setDate(Date date) {
		this.date = date;
	}

	public RequestLogBean() {
	}

	public RequestLogBean(URI uri) {
		new RequestLogBean(new Date(), uri.toString());
	}

	public String getUri() {
		return uri;
	}

	public void setUri(String uri) {
		this.uri = uri;
	}
}
