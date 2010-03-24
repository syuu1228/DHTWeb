/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.dhtfox;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.net.URLConnection;
import java.util.Set;
import java.util.logging.Logger;
import ow.dht.DHT;
import ow.dht.ValueInfo;
import ow.id.ID;
import ow.routing.RoutingException;

/**
 *
 * @author syuu
 */
public class HTTPServer {
    private int port, httpTimeout;
    private final DHT<String> dht;
    private Proxy proxy;
    private HttpHandler requestHandler = new HttpHandler() {
        @Override
        public void handle(HttpExchange he) throws IOException {

        }
    };

    public HTTPServer(int port, DHT<String> dht, Proxy proxy, int httpTimeout) {
        this.port = port;
        this.dht = dht;
        this.proxy = proxy;
        this.httpTimeout = httpTimeout;
    }

    public void bind() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        HttpHandler proxyHandler = new ProxyHandler(dht, proxy, httpTimeout);
        server.createContext("/proxy/", proxyHandler);
        server.createContext("/request/", requestHandler);
        server.start();
    }
}
