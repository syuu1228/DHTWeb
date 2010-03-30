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

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import netscape.javascript.JSObject;
import ow.dht.DHT;

/**
 *
 * @author syuu
 */
public class HTTPServer {

    private int port, httpTimeout;
    private final DHT<String> dht;
    private Proxy proxy;
    private final JSObject cacheCallback;

    public static void main(String[] args) throws IOException {
        HTTPServer httpd = new HTTPServer(8080, null, Proxy.NO_PROXY, 1000, null);
        httpd.bind();
    }

    public HTTPServer(int port, DHT<String> dht, Proxy proxy, int requestTimeout, JSObject cacheCallback) {
        this.port = port;
        this.dht = dht;
        this.proxy = proxy;
        this.httpTimeout = requestTimeout;
        this.cacheCallback = cacheCallback;
    }

    public void bind() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        HttpHandler proxyHandler = new ProxyHandler(dht, proxy, httpTimeout);
        HttpHandler requestHandler = new RequestHandler(cacheCallback);
        server.createContext("/proxy/", proxyHandler);
        server.createContext("/request/", requestHandler);
        server.start();
    }
}
