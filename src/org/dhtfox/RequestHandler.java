/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.dhtfox;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
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

    public RequestHandler(JSObject callback) {
        this.callback = callback;
    }

    @Override
    public void handle(HttpExchange he) throws IOException {

    }

}
