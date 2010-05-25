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
package org.dhtweb;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.Proxy;
import java.net.URI;
import java.net.URL;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ow.dht.DHT;
import ow.dht.ValueInfo;
import ow.id.ID;
import ow.routing.RoutingException;

public class PrefetchTask implements Runnable {
	private final static Logger logger = LoggerFactory
			.getLogger(PrefetchTask.class);
	private final DHT<String> dht;
	private final int port;
	private final InetAddress selfAddress;
	private final Proxy proxy;
	private final URI uri;
	private final int httpTimeout;
	private final ExecutorService putExecutor;

	public PrefetchTask(DHT<String> dht, int port, InetAddress selfAddress,
			Proxy proxy, int httpTimeout, ExecutorService putExecutor, URI uri) {
		this.dht = dht;
		this.port = port;
		this.selfAddress = selfAddress;
		this.proxy = proxy;
		this.httpTimeout = httpTimeout;
		this.uri = uri;
		this.putExecutor = putExecutor;
	}

	@Override
	public void run() {
		try {
			ID key = ID.getSHA1BasedID(uri.toString().getBytes());
			Set<ValueInfo<String>> remoteAddrs = dht.get(key);

			for (ValueInfo<String> v : remoteAddrs) {
				HttpURLConnection connection = null;
				try {
					URL remoteUrl = new URL("http://" + v.getValue()
							+ "/request/" + uri.toString());

					if (v.getValue().equals(
							selfAddress.getHostAddress() + ":" + port)) {
						logger.info("Got self url, skipping: {}", remoteUrl);
						continue;
					}
					logger.info("Got url from DHT: {}", remoteUrl);
					connection = (HttpURLConnection) remoteUrl
							.openConnection(proxy);
					connection.setConnectTimeout(httpTimeout);
					connection.setRequestProperty("Host", remoteUrl.getHost());
					connection.connect();
					int response = connection.getResponseCode();
					logger.info("HTTP response: {}", response);
					if (response == HttpURLConnection.HTTP_OK) {
						InputStream in = null;
						try {
							in = connection.getInputStream();
							HttpUtil.sendBodyToNull(in, connection
									.getContentLength());
						} catch (IOException e) {
							logger.warn(e.getMessage(), e);
						} finally {
							try {
								in.close();
							} catch (Exception e) {
							}
							try {
								connection.disconnect();
							} catch (Exception e) {
							}
						}
						putExecutor.submit(new PutTask(dht, port, key, selfAddress, uri));
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
		} catch (RoutingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
