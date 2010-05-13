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
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ExecutorService;

//import org.dhtfox.log.ProxyLogBean;
//import org.dhtfox.log.ProxyLogWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ow.dht.DHT;
import ow.dht.ValueInfo;
import ow.id.ID;
import ow.messaging.MessagingAddress;
import ow.routing.RoutingException;

/**
 * 
 * @author syuu
 */
public class ProxyHandler implements HttpHandler {

	final static Logger logger = LoggerFactory.getLogger(ProxyHandler.class);
	private final DHT<String> dht;
	private final int port, httpTimeout;
	private final Proxy proxy;
	private final ExecutorService putExecutor;
//	private ProxyLogWriter proxyLogger;

	ProxyHandler(DHT<String> dht, Proxy proxy, int port, int httpTimeout,
			ExecutorService putExecutor) {
		this.dht = dht;
		this.proxy = proxy;
		this.port = port;
		this.httpTimeout = httpTimeout;
		this.putExecutor = putExecutor;
/*
		try {
			this.proxyLogger = new ProxyLogWriter("proxy.log");
			this.proxyLogger.open();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
*/
	}
/*
	protected void finalize() throws Throwable {
		this.proxyLogger.close();
	}
*/
	@SuppressWarnings("unchecked")
	private boolean proxyToLocalCache(HttpExchange he, URI uri) {
		File file = LocalResponseCache.getLocalFile(uri);
		File headerFile = LocalResponseCache.getLocalHeader(uri);
		FileInputStream fisHeader = null;
		ObjectInputStream ois = null;

		if (!file.exists() || !headerFile.exists()) {
			return false;
		}

		try {
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

			he.sendResponseHeaders(HttpURLConnection.HTTP_OK, Integer
					.parseInt(headerMap.get("Content-Length").get(0)));
		} catch (Exception e) {
			logger.warn(e.getMessage(), e);
			try {
				he.sendResponseHeaders(HttpURLConnection.HTTP_BAD_REQUEST, 0);
			} catch (IOException ex) {
			}
		}
		try {
			BufferedInputStream in = new BufferedInputStream(
					new FileInputStream(file));
			OutputStream out = he.getResponseBody();
			byte[] buf = new byte[65535];
			int size = -1;
			while ((size = in.read(buf)) != -1) {
				out.write(buf, 0, size);
			}
			out.flush();
		} catch (IOException e) {
			logger.warn(e.getMessage(), e);
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
		}
		return true;
	}

	private boolean proxyToDHT(HttpExchange he, ID key, URI uri)
			throws RoutingException {
		logger.info("request key to DHT:{}", key);
		Set<ValueInfo<String>> remoteAddrs = dht.get(key);
		logger.info("got {} entries", remoteAddrs.size());
		for (ValueInfo<String> v : remoteAddrs) {
			HttpURLConnection connection = null;
			try {
				URL remoteUrl = new URL("http://" + v.getValue() + "/request/"
						+ uri.toString());
				MessagingAddress selfAddress = dht.getSelfAddress();

				if (v.getValue().equals(
						selfAddress.getHostAddress() + ":" + port)) {
					logger.info("Got self url, skipping: {}", remoteUrl);
					continue;
				}
				logger.info("Got url from DHT: {}", remoteUrl);

				connection = (HttpURLConnection) remoteUrl
						.openConnection(proxy);
				connection.setConnectTimeout(httpTimeout);
				connection.setRequestMethod(he.getRequestMethod());
				setRequestProperties(connection, he.getRequestHeaders());
				connection.setRequestProperty("Host", remoteUrl.getHost());
				connection.connect();
				int response = connection.getResponseCode();
				logger.info("HTTP response: {}", response);
				if (response == HttpURLConnection.HTTP_OK) {
					InputStream in = null;
					OutputStream out = null;
					try {
						setResponseHeaders(he.getResponseHeaders(), connection
								.getHeaderFields());
						he.sendResponseHeaders(HttpURLConnection.HTTP_OK,
								connection.getContentLength());
						in = connection.getInputStream();
						out = he.getResponseBody();
						HttpUtil.sendBody(out, in, connection
								.getContentLength());
					} catch (IOException e) {
						logger.warn(e.getMessage(), e);
					} finally {
						try {
							out.close();
						} catch (Exception e) {
						}
						try {
							in.close();
						} catch (Exception e) {
						}
						try {
							connection.disconnect();
						} catch (Exception e) {
						}
					}
					logger.info("Request handled by DHT");
					return true;
				}
			} catch (IOException e) {
				logger.warn(e.getMessage(), e);
			} finally {
				try {
					connection.disconnect();
				} catch (Exception e) {
				}
			}
		}
		logger.info("Request not handled by DHT");
		return false;
	}

	private boolean proxyToOriginalServer(HttpExchange he, URI uri) {
		logger.info("Request proxying:{}", uri);
		HttpURLConnection connection = null;
		InputStream in = null;
		OutputStream out = null;
		boolean result = false;
		try {
			connection = (HttpURLConnection) uri.toURL().openConnection(proxy);
			connection.setConnectTimeout(httpTimeout);
			connection.setRequestMethod(he.getRequestMethod());
			setRequestProperties(connection, he.getRequestHeaders());
			connection.setRequestProperty("Host", uri.getHost());
			connection.connect();
			int responseCode = connection.getResponseCode();
			logger.info("responseCode:{}", responseCode);
			setResponseHeaders(he.getResponseHeaders(), connection
					.getHeaderFields());
			if (responseCode == HttpURLConnection.HTTP_OK) {
				int length = connection.getContentLength();
				he.sendResponseHeaders(HttpURLConnection.HTTP_OK, length);
				in = connection.getInputStream();
				out = he.getResponseBody();
				HttpUtil.sendBody(out, in, length);
				result = true;
			} else {
				he.sendResponseHeaders(responseCode, 0);
			}
		} catch (Exception e) {
			logger.warn(e.getMessage(), e);
		} finally {
			try {
				he.getRequestBody().close();
			} catch (Exception e) {
			}
			try {
				he.getResponseBody().close();
			} catch (Exception e) {
			}
			try {
				if (in != null) {
					in.close();
				}
			} catch (Exception e) {
			}
			try {
				connection.disconnect();
			} catch (Exception e) {
			}
		}
		logger.info("Request done");
		return result;
	}

	@Override
	public void handle(HttpExchange he) throws IOException {
		URI uri = null;
		ID key = null;
		if (!he.getRemoteAddress().getAddress().isLoopbackAddress()) {
			logger.info("/proxy/ accessed from external address:"
					+ he.getRemoteAddress());
			he.sendResponseHeaders(HttpURLConnection.HTTP_FORBIDDEN, 0);
			try {
				he.getRequestBody().close();
			} catch (Exception e) {
			}
			try {
				he.getResponseBody().close();
			} catch (Exception e) {
			}
			return;
		}
		boolean dhttest = false, passthroughtest = false;
		try {
			if (he.getRequestURI().getPath().startsWith("/dhttest/")) {
				uri = new URI(he.getRequestURI().getPath().replaceFirst(
						"^/dhttest/", ""));
				dhttest = true;
			} else if (he.getRequestURI().getPath().startsWith(
					"/passthroughtest/")) {
				uri = new URI(he.getRequestURI().getPath().replaceFirst(
						"^/passthroughtest/", ""));
				passthroughtest = true;
			} else {
				uri = new URI(he.getRequestURI().getPath().replaceFirst(
						"^/proxy/", ""));
			}
			logger.info("uri:{}", uri);
			key = ID.getSHA1BasedID(uri.toString().getBytes());
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

		if (!passthroughtest) {
			if (!dhttest) {
				System.out.printf("start LocalCache %s %s\n", new Date(), uri);
				long currentTime = System.currentTimeMillis();
				boolean result = proxyToLocalCache(he, uri);
				System.out.printf("end LocalCache %s %s %b %d\n", new Date(), uri, result, System.currentTimeMillis() - currentTime);
				if (result) {
/*
					try {
						proxyLogger.write(new ProxyLogBean(uri, "cache"));
					} catch (Exception e) {
						logger.warn(e.getMessage(), e);
					}
*/
					return;
				}
			}
			try {
				System.out.printf("start DHT %s %s\n", new Date(), uri);
				long currentTime = System.currentTimeMillis();
				boolean result = proxyToDHT(he, key, uri);
				System.out.printf("end DHT %s %s %b %d\n", new Date(), uri, result, System.currentTimeMillis() - currentTime);
				if (result) {
/*
					try {
						proxyLogger.write(new ProxyLogBean(uri, "dht"));
					} catch (Exception e) {
						logger.warn(e.getMessage(), e);
					}
*/
					putCache(key);
					return;
				}
				if (dhttest) {
					he.sendResponseHeaders(HttpURLConnection.HTTP_NOT_FOUND, 0);
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
			} catch (RoutingException e) {
				logger.info(e.getMessage());
			}
		}
		System.out.printf("start OriginalServer %s %s\n", new Date(), uri);		
		long currentTime = System.currentTimeMillis();
		boolean result = proxyToOriginalServer(he, uri);
		System.out.printf("end OriginalServer %s %s %b %d\n", new Date(), uri, result, System.currentTimeMillis() - currentTime);
		if (result) {
/*
			try {
				proxyLogger.write(new ProxyLogBean(uri, "http"));
			} catch (Exception e) {
				logger.warn(e.getMessage(), e);
			}
*/
			putCache(key);
		}
	}

	private void setResponseHeaders(Headers responseHeaders,
			Map<String, List<String>> headerFields) {
		for (Entry<String, List<String>> entry : headerFields.entrySet()) {
			String key = entry.getKey();
			List<String> values = entry.getValue();
			if (key != null) {
				if (key.toLowerCase().equals("host")) {
					continue;
				}
				for (String val : values) {
					logger.info("response key:{} value:{}", key, val);
				}
				responseHeaders.put(key, values);
			}
		}
	}

	private void setRequestProperties(HttpURLConnection connection,
			Headers requestHeaders) {
		for (Entry<String, List<String>> entry : requestHeaders.entrySet()) {
			String key = entry.getKey();
			List<String> values = entry.getValue();
			if (key != null) {
				if (key.toLowerCase().equals("host")) {
					continue;
				}
				for (String val : values) {
					logger.info("request key:{} value:{}", key, values);
					connection.setRequestProperty(key, val);
				}
			}
		}
	}

	private void putCache(ID key) {
		putExecutor.submit(new PutTask(dht, port, key));
	}
}
