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
import dareka.processor.HttpUtil;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
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

    ProxyHandler(DHT<String> dht, Proxy proxy, int port, int httpTimeout, ExecutorService putExecutor) {
        this.dht = dht;
        this.proxy = proxy;
        this.port = port;
        this.httpTimeout = httpTimeout;
        this.putExecutor = putExecutor;
    }

    public boolean proxyToDHT(HttpExchange he, ID key, URL url) throws RoutingException {
        logger.info("request key to DHT:{}", key);
        Set<ValueInfo<String>> remoteAddrs = dht.get(key);
        logger.info("got {} entries", remoteAddrs.size());
        for (ValueInfo<String> v : remoteAddrs) {
            HttpURLConnection connection = null;
            try {
                URL remoteUrl = new URL("http://"+ v.getValue() + "/request/" + url.toString());
                MessagingAddress selfAddress = dht.getSelfAddress();

                if(v.getValue().equals(selfAddress.getHostAddress() + ":" + port)) {
                    logger.info("Got self url, skipping: {}", remoteUrl);
                    continue;
                }
                logger.info("Got url from DHT: {}", remoteUrl);

                connection = (HttpURLConnection) remoteUrl.openConnection(proxy);
                connection.setConnectTimeout(httpTimeout);
                connection.connect();
                int response = connection.getResponseCode();
                logger.info("HTTP response: {}", response);
                if (response == HttpURLConnection.HTTP_OK) {
                    InputStream in = null;
                    OutputStream out = null;
                    try {
                        setResponseHeaders(he.getResponseHeaders(), connection.getHeaderFields());
                        he.sendResponseHeaders(HttpURLConnection.HTTP_OK, connection.getContentLength());
                        in = connection.getInputStream();
                        out = he.getResponseBody();
                        HttpUtil.sendBody(out, in, connection.getContentLength());
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
                        logger.info("Request handled by DHT");
                        return true;
                    }
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

    private void proxyToOriginalServer(HttpExchange he, URL url) {
        logger.info("Request proxying");
        HttpURLConnection connection = null;
        InputStream in = null;
        OutputStream out = null;
        try {
            connection = (HttpURLConnection)url.openConnection(proxy);
            connection.setConnectTimeout(httpTimeout);
            connection.connect();
            setResponseHeaders(he.getResponseHeaders(), connection.getHeaderFields());
            int responseCode = connection.getResponseCode();
            he.sendResponseHeaders(responseCode, connection.getContentLength());
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
            try {
                connection.disconnect();
            } catch (Exception e) {
            }
        }
        logger.info("Request done");
    }

    @Override
    public void handle(HttpExchange he) throws IOException {
        URL url = null;
        ID key = null;
        if (!he.getRemoteAddress().getAddress().isLoopbackAddress()) {
            logger.info("/proxy/ accessed from external address:" + he.getRemoteAddress());
            he.sendResponseHeaders(HttpURLConnection.HTTP_FORBIDDEN, 0);
            try {he.getRequestBody().close(); }catch(Exception e){}
            try {he.getResponseBody().close(); }catch(Exception e){}
            return;
        }
        boolean dhttest = false, passthroughtest = false;
        try {
            if (he.getRequestURI().getPath().startsWith("/dhttest/")) {
                url = new URL(he.getRequestURI().getPath().replaceFirst("^/dhttest/", ""));
                dhttest = true;
            } else if(he.getRequestURI().getPath().startsWith("/passthroughtest/")) {
                url = new URL(he.getRequestURI().getPath().replaceFirst("^/passthroughtest/", ""));
                passthroughtest = true;
            } else {
                url = new URL(he.getRequestURI().getPath().replaceFirst("^/proxy/", ""));
            }
            logger.info("url:{}", url);
            key = ID.getSHA1BasedID(url.toString().getBytes());
        } catch (MalformedURLException e) {
            logger.info(e.getMessage());
            he.sendResponseHeaders(HttpURLConnection.HTTP_BAD_REQUEST, 0);
            try {he.getRequestBody().close(); }catch(Exception e1){}
            try {he.getResponseBody().close(); }catch(Exception e1){}
            return;
        }

        if (!passthroughtest)
        try {
            boolean result = proxyToDHT(he, key, url);
            if (result) {
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
        proxyToOriginalServer(he, url);
        putCache(key);
    }

    private void setResponseHeaders(Headers responseHeaders, Map<String, List<String>> headerFields) {
        for (Map.Entry<String, List<String>> entry : headerFields.entrySet()) {
            String key = entry.getKey();
            List<String> values = entry.getValue();
            if (key != null) {
                responseHeaders.put(key, values);
            }
        }
    }

    private void putCache(ID key) {
        putExecutor.submit(new PutTask(dht, port, key));
    }

}
