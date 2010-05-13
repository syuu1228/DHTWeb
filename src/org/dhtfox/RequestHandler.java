/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.dhtfox;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

//import org.dhtfox.log.RequestLogBean;
//import org.dhtfox.log.RequestLogWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 
 * @author syuu
 */
public class RequestHandler implements HttpHandler {

	final static Logger logger = LoggerFactory.getLogger(RequestHandler.class);
//	private RequestLogWriter requestLogger;

	RequestHandler() {
		/*
		try {
			this.requestLogger = new RequestLogWriter("request.log");
			this.requestLogger.open();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		*/
	}
	
	protected void finalize() throws Throwable {
//		this.requestLogger.close();
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public void handle(HttpExchange he) throws IOException {
		logger.info("request handling");

		URI uri = null;
		URI u = he.getRequestURI();
		try {
			uri = new URI(u.getPath().replaceFirst("^/request/", ""));
		} catch (URISyntaxException ex) {
			logger.warn(ex.getMessage(), ex);
			he.sendResponseHeaders(HttpURLConnection.HTTP_BAD_REQUEST, 0);
			try {
				he.getRequestBody().close();
			} catch (Exception e1) {
			}
			try {
				he.getResponseBody().close();
			} catch (Exception e1) {
			}
			return;
		}
		logger.info("url:{}", uri);
		System.out.printf("start request %s %s\n", new Date(), uri);
		long currentTime = System.currentTimeMillis();
		
		File file = LocalResponseCache.getLocalFile(uri);
		File headerFile = LocalResponseCache.getLocalHeader(uri);
		FileInputStream fisHeader = null;
		ObjectInputStream ois = null;
		try {
			if (!file.exists()) {
				he.sendResponseHeaders(HttpURLConnection.HTTP_NOT_FOUND, 0);
				try {
					he.getRequestBody().close();
				} catch (Exception e1) {
				}
				try {
					he.getResponseBody().close();
				} catch (Exception e1) {
				}
				System.out.printf("end request %s %s false %d\n", new Date(), uri, System.currentTimeMillis() - currentTime);
				return;
			}

			fisHeader = new FileInputStream(headerFile);
			ois = new ObjectInputStream(fisHeader);
			Headers headers = he.getResponseHeaders();
			Map<String, List<String>> headerMap = (Map<String, List<String>>) ois
					.readObject();
			Map<String, List<String>> tmp = new HashMap<String, List<String>>();
			for (String key : headerMap.keySet())
				if (key != null)
					tmp.put(key, headerMap.get(key));
			headers.putAll(tmp);

			BufferedInputStream in = new BufferedInputStream(
					new FileInputStream(file));
			he.sendResponseHeaders(HttpURLConnection.HTTP_OK, Integer
					.parseInt(headerMap.get("Content-Length").get(0)));
			OutputStream out = he.getResponseBody();
			byte[] buf = new byte[65535];
			int size = -1;
			while ((size = in.read(buf)) != -1) {
				out.write(buf, 0, size);
			}
			out.flush();
		} catch (Exception e) {
			logger.warn(e.getMessage(), e);
			he.sendResponseHeaders(HttpURLConnection.HTTP_BAD_REQUEST, 0);
		} finally {
			try {
				fisHeader.close();
				ois.close();
			} catch (IOException e) {
			}

			try {
				he.getRequestBody().close();
			} catch (Exception e1) {
			}
			try {
				he.getResponseBody().close();
			} catch (Exception e1) {
			}
			/*
			try {
				requestLogger.write(new RequestLogBean(uri));
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			*/
			System.out.printf("end request %s %s true %d\n", new Date(), uri, System.currentTimeMillis() - currentTime);
		}
	}
}
