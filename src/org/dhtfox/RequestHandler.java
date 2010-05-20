/*
 * Copyright 2010 syuu, and contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 
 * @author syuu
 */
public class RequestHandler implements HttpHandler {

	final static Logger logger = LoggerFactory.getLogger(RequestHandler.class);
	final static Logger requestLogger = LoggerFactory.getLogger("requestlog");

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

		long currentTime = System.currentTimeMillis();
		
		File file = LocalResponseCache.getLocalFile(uri);
		File headerFile = LocalResponseCache.getLocalHeader(uri);
		FileInputStream fisHeader = null;
		ObjectInputStream ois = null;
		boolean headerSent = false;
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

				requestLogger.info(
						"false.{}.{}", uri,
						System.currentTimeMillis() - currentTime);
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
			headerSent = true;
			OutputStream out = he.getResponseBody();
			byte[] buf = new byte[65535];
			int size = -1;
			while ((size = in.read(buf)) != -1) {
				out.write(buf, 0, size);
			}
			out.flush();
		} catch (Exception e) {
			logger.warn(e.getMessage(), e);
			if(!headerSent)
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
			requestLogger.info(
					"true,{},{}", uri,
					System.currentTimeMillis() - currentTime);
		}
	}
}
