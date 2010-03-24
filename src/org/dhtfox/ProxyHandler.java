/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.dhtfox;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import dareka.common.CloseUtil;
import dareka.processor.HttpUtil;
import dareka.processor.Resource;
import dareka.processor.Resource.Type;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.URL;
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
        Set<ValueInfo<String>> remoteAddrs = dht.get(key);
        for (ValueInfo<String> v : remoteAddrs) {
            HttpURLConnection connection = null;
            try {
                URL remoteUrl = new URL(v.getValue());
                logger.info("Got url from DHT: "+ remoteUrl);
                connection = (HttpURLConnection) remoteUrl.openConnection(proxy);
                connection.setConnectTimeout(httpTimeout);
                connection.connect();
                int response = connection.getResponseCode();
                if (response == HttpURLConnection.HTTP_OK) {
                    he.getResponseHeaders().set("Content-Type", connection.getContentType());
                    he.sendResponseHeaders(HttpURLConnection.HTTP_OK, connection.getContentLength());
                    OutputStream out = he.getResponseBody();
                    InputStream in = connection.getInputStream();
                    try {
                        HttpUtil.sendBody(out, in, connection.getContentLength());
                    } catch (IOException e) {
                        logger.info(e.getMessage());
                    } finally {
                        CloseUtil.close(out);
                        CloseUtil.close(in);
                        connection.disconnect();
                        logger.info("Request handled by DHT");
                        return true;
                    }
                }
            } catch (IOException e) {
                logger.info(e.getMessage());
            } finally {
                if (connection != null)
                    connection.disconnect();
            }
        }
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
        } catch (MalformedURLException e) {
            e.printStackTrace();
            he.sendResponseHeaders(HttpURLConnection.HTTP_BAD_REQUEST, 0);
            he.getRequestBody().close();
        }
        try {
            boolean result = proxyToDHT(he, url);
            if (result)
                return;
        } catch (RoutingException e) {
            logger.info(e.getMessage());
        }
        Resource.get(Resource.Type.URL, url.toString());
    }

}
