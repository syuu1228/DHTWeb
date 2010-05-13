package org.dhtfox.log;

import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;

import org.supercsv.io.CsvBeanWriter;
import org.supercsv.io.ICsvBeanWriter;
import org.supercsv.prefs.CsvPreference;

public class ProxyLogWriter {
	ICsvBeanWriter outFile;
	String fileName;

	public ProxyLogWriter(String fileName) throws IOException {
		this.fileName = fileName;
	}

	public void open() throws IOException {
		outFile = new CsvBeanWriter(new FileWriter(fileName),
				CsvPreference.EXCEL_PREFERENCE);
		outFile.writeHeader(ProxyLogBean.HEADER);
	}

	public void write(ProxyLogBean bean) throws IOException,
			IllegalAccessException, InvocationTargetException {
		outFile.write(bean, ProxyLogBean.HEADER,
				ProxyLogBean.PROCESSORS);
	}

	public void close() throws IOException {
		outFile.close();
	}

	public void writeAll(ProxyLogBean[] beans) throws IOException,
			IllegalAccessException, InvocationTargetException {
		for (ProxyLogBean bean : beans)
			write(bean);
	}
}
