package org.dhtfox.log;

import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.supercsv.io.CsvBeanWriter;
import org.supercsv.io.ICsvBeanWriter;
import org.supercsv.prefs.CsvPreference;

public class AccessLogWriter {

	final static Logger logger = LoggerFactory.getLogger(AccessLogWriter.class);
	ICsvBeanWriter outFile;
	String fileName;

	public AccessLogWriter(String fileName) throws IOException {
		this.fileName = fileName;
	}

	public void open() throws IOException {
		outFile = new CsvBeanWriter(new FileWriter(fileName),
				CsvPreference.EXCEL_PREFERENCE);
		outFile.writeHeader(AccessLogBean.HEADER);
	}

	public void write(AccessLogBean bean) {
		try {
			outFile.write(bean, AccessLogBean.HEADER,
					AccessLogBean.PROCESSORS);
		} catch (Exception e) {
			logger.warn(e.getMessage(), e);
		}
	}

	public void close() throws IOException {
		outFile.close();
	}

	public void writeAll(AccessLogBean[] beans) throws IOException,
			IllegalAccessException, InvocationTargetException {
		for (AccessLogBean bean : beans)
			write(bean);
	}
}
