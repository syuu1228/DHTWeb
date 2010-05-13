package org.dhtfox.log;

import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;

import org.supercsv.io.CsvBeanWriter;
import org.supercsv.io.ICsvBeanWriter;
import org.supercsv.prefs.CsvPreference;

public class RequestLogWriter {
	ICsvBeanWriter outFile;
	String fileName;

	public RequestLogWriter(String fileName) throws IOException {
		this.fileName = fileName;
	}

	public void open() throws IOException {
		outFile = new CsvBeanWriter(new FileWriter(fileName),
				CsvPreference.EXCEL_PREFERENCE);
		outFile.writeHeader(RequestLogBean.HEADER);
	}

	public void write(RequestLogBean bean) throws IOException,
			IllegalAccessException, InvocationTargetException {
		outFile.write(bean, RequestLogBean.HEADER,
				RequestLogBean.PROCESSORS);
	}

	public void close() throws IOException {
		outFile.close();
	}

	public void writeAll(RequestLogBean[] beans) throws IOException,
			IllegalAccessException, InvocationTargetException {
		for (RequestLogBean bean : beans)
			write(bean);
	}
}
