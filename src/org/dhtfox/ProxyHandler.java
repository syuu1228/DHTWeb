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

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import dareka.processor.HttpUtil;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ow.dht.DHT;
import ow.dht.ValueInfo;
import ow.id.ID;
import ow.routing.RoutingException;

/**
 *
 * @author syuu
 */
public class ProxyHandler implements HttpHandler {

    final static Logger logger = LoggerFactory.getLogger(ProxyHandler.class);
    private final DHT<String> dht;
    private final int httpTimeout;
    private final Proxy proxy;

    ProxyHandler(DHT<String> dht, Proxy proxy, int httpTimeout) {
        this.dht = dht;
        this.proxy = proxy;
        this.httpTimeout = httpTimeout;
    }

    public boolean proxyToDHT(HttpExchange he, URL url) throws RoutingException {
        ID key = ID.getSHA1BasedID(url.toString().getBytes());
        logger.info("request key to DHT:{}", key);
        Set<ValueInfo<String>> remoteAddrs = dht.get(key);
        for (ValueInfo<String> v : remoteAddrs) {
            HttpURLConnection connection = null;
            try {
                URL remoteUrl = new URL(v.getValue());
                logger.info("Got url from DHT: [}", remoteUrl);
                connection = (HttpURLConnection) remoteUrl.openConnection(proxy);
                connection.setConnectTimeout(httpTimeout);
                connection.connect();
                int response = connection.getResponseCode();
                if (response == HttpURLConnection.HTTP_OK) {
                    logger.info("HTTP response == OK");
                    he.getResponseHeaders().set("Content-Type", connection.getContentType());
                    he.sendResponseHeaders(HttpURLConnection.HTTP_OK, connection.getContentLength());
                    OutputStream out = he.getResponseBody();
                    InputStream in = connection.getInputStream();
                    try {
                        HttpUtil.sendBody(out, in, connection.getContentLength());
                    } catch (IOException e) {
                        logger.info(e.getMessage());
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
                        logger.info("Request handled by DHT");
                        return true;
                    }
                }
            } catch (IOException e) {
                logger.info(e.getMessage());
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

    @Override
    public void handle(HttpExchange he) throws IOException {
        URL url = null;

        if (!he.getRemoteAddress().getAddress().isLoopbackAddress()) {
            logger.info("/proxy/ accessed from external address:" + he.getRemoteAddress());
            he.sendResponseHeaders(HttpURLConnection.HTTP_FORBIDDEN, 0);
            he.getRequestBody().close();
        }
        try {
            url = new URL(he.getRequestURI().getPath().replaceFirst("^/proxy/", ""));
            logger.info("url:{}", url);
        } catch (MalformedURLException e) {
            logger.info(e.getMessage());
            he.sendResponseHeaders(HttpURLConnection.HTTP_BAD_REQUEST, 0);
            he.getRequestBody().close();
        }
        /*
        try {
        boolean result = proxyToDHT(he, url);
        if (result) {
        return;
        }
        } catch (RoutingException e) {
        logger.info(e.getMessage());
        }
         */
        logger.info("Request proxying");
        InputStream in = null;
        OutputStream out = null;
        try {
            URLConnection connection = url.openConnection(proxy);
            connection.connect();
            for (Map.Entry<String, List<String>> entry : connection.getHeaderFields().entrySet()) {
                String key = entry.getKey();
                List<String> values = entry.getValue();
                if (key != null) {
                    he.getResponseHeaders().put(key, values);
                }
            }
            he.sendResponseHeaders(HttpURLConnection.HTTP_OK, 0);
            in = connection.getInputStream();
            out = he.getResponseBody();
            HttpUtil.sendBody(out, in, connection.getContentLength());
        } catch (Exception e) {
            e.printStackTrace();
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
        }
        logger.info("Request done");
    }
}
