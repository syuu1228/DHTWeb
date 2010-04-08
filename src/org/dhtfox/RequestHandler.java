/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.dhtfox;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import dareka.processor.HttpResponseHeader;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.List;
import java.util.Map;
import netscape.javascript.JSObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author syuu
 */
public class RequestHandler implements HttpHandler {

    final static Logger logger = LoggerFactory.getLogger(RequestHandler.class);
    private final JSObject callback;
    private final int port;

    public RequestHandler(JSObject callback, int port) {
        this.callback = callback;
        this.port = port;
    }

    @Override
    public void handle(HttpExchange he) throws IOException {
        logger.info("request handling");

        URL url = null;
        try {
            URI u = he.getRequestURI();
            url = new URL("http://127.0.0.1:" + port + u.getPath().replaceFirst("^/request/", "/proxy/"));
            logger.info("url:{}", url);
        } catch (MalformedURLException e) {
            logger.warn(e.getMessage(), e);
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
        JSObject cacheEntry = null;
        cacheEntry = (JSObject) callback.call("getCacheEntry", new Object[]{url.toString()});
        if (cacheEntry == null) {
            logger.info("cacheEntry == null");
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
        String fileName = null;
        File file = null;
        try {
            String responseHeader = (String) cacheEntry.call("getMetaDataElement", new Object[]{"response-head"});
            logger.info("responseHeader:{}", responseHeader);
            HttpResponseHeader parser = new HttpResponseHeader(responseHeader);
            Headers headers = new Headers();
            for (Map.Entry<String, List<String>> entry : parser.getMessageHeaders().entrySet()) {
                String key = entry.getKey();
                List<String> values = entry.getValue();
                for (String value : values) {
                    logger.info("key:{} value:{}", key, value);
                }
                if (key != null) {
                    headers.put(key, values);
                }
            }
            fileName = (String) callback.call("readAll", new Object[]{cacheEntry});
            logger.info("fileName:{}", fileName);
            if (fileName == null) {
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
            file = new File(fileName);
            BufferedInputStream in = new BufferedInputStream(new FileInputStream(fileName));
            he.sendResponseHeaders(HttpURLConnection.HTTP_OK, file.length());
            OutputStream out = he.getResponseBody();
            byte[] buf = new byte[65535];
            int size = -1;
            while ((size = in.read(buf)) != -1) {
                out.write(buf);
            }
            out.flush();
        } catch (Exception e) {
            logger.warn(e.getMessage(), e);
            he.sendResponseHeaders(HttpURLConnection.HTTP_BAD_REQUEST, 0);
        } finally {
            try {
                he.getRequestBody().close();
            } catch (Exception e1) {
            }
            try {
                he.getResponseBody().close();
            } catch (Exception e1) {
            }
            file.delete();
        }
    }
}
