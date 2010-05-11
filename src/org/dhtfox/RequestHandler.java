/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
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
    private final int port;

    public RequestHandler(int port) {
        this.port = port;
    }

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
        File file = LocalResponseCache.getLocalFile(uri);
        File headerFile = LocalResponseCache.getLocalHeader(uri);
        FileInputStream fisHeader = null;
        ObjectInputStream ois = null;
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
                return;
            }

            fisHeader = new FileInputStream(headerFile);
            ois = new ObjectInputStream(fisHeader);
            Headers headers = he.getResponseHeaders();
            headers.putAll((Map<String, List<String>>) ois.readObject());

            BufferedInputStream in = new BufferedInputStream(new FileInputStream(file.getName()));
            he.sendResponseHeaders(HttpURLConnection.HTTP_OK, file.length());
            OutputStream out = he.getResponseBody();
            byte[] buf = new byte[65535];
            int size = -1;
            while ((size = in.read(buf)) != -1) {
                out.write(buf, 0, size);
            }
            out.flush();
        } catch (Exception e) {
            logger.warn(e.getMessage(), e);
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
        }
    }
}
