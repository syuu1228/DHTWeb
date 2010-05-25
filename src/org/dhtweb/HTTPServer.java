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

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.concurrent.ExecutorService;
import ow.dht.DHT;

/**
 * 
 * @author syuu
 */
public class HTTPServer {
	private final int port, httpTimeout;
	private final DHT<String> dht;
	private final Proxy proxy;
	private final HttpServer server;
	private final ExecutorService putExecutor, prefetchExecutor;
	private final InetAddress selfAddress;


	public HTTPServer(int port, DHT<String> dht, Proxy proxy,
			int requestTimeout, ExecutorService putExecutor, ExecutorService prefetchExecutor, 
			InetAddress selfAddress) throws IOException {
		this.port = port;
		this.dht = dht;
		this.proxy = proxy;
		this.httpTimeout = requestTimeout;
		this.putExecutor = putExecutor;
		this.prefetchExecutor = prefetchExecutor;
		this.server = HttpServer.create(new InetSocketAddress(port), 0);
		this.selfAddress = selfAddress;
	}

	public void bind() {
		HttpHandler proxyHandler = new ProxyHandler(dht, proxy, port,
				httpTimeout, putExecutor, prefetchExecutor, selfAddress);
		HttpHandler requestHandler = new RequestHandler();
		server.createContext("/proxy/", proxyHandler);
		server.createContext("/dhttest/", proxyHandler);
		server.createContext("/passthroughtest/", proxyHandler);
		server.createContext("/request/", requestHandler);
		server.start();
	}

	public void stop() {
		server.stop(0);
	}
}
