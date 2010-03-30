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

import java.net.Proxy;
import netscape.javascript.JSObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.bridge.SLF4JBridgeHandler;
import org.slf4j.impl.FirefoxLogger;
import ow.dht.ByteArray;
import ow.dht.DHT;
import ow.dht.DHTConfiguration;
import ow.dht.DHTFactory;

public class DHTFox {

    static {
        SLF4JBridgeHandler.install();
    }
    private static final Logger logger = LoggerFactory.getLogger(DHTFox.class);
    public static final short APPLICATION_ID = 0x0876;
    public static final short APPLICATION_MAJOR_VERSION = 0;
    public static final Proxy PROXY_SETTING = Proxy.NO_PROXY;
    public static final int HTTP_REQUEST_TIMEOUT = 1000;
    private ByteArray hashedSecret;
    private DHT<String> dht = null;
    private HTTPServer http = null;

    public DHT<String> getDHT() {
        return dht;
    }

    public void start(String secret, boolean upnpEnable, String bootstrapNode, int dhtPort, int httpPort, JSObject cacheCallback, JSObject loggerCallback) throws Exception {
        try {
            FirefoxLogger.setJSCallback(loggerCallback);
            this.hashedSecret = new ByteArray(secret.getBytes("UTF-8")).hashWithSHA1();
            DHTConfiguration config = DHTFactory.getDefaultConfiguration();
            config.setImplementationName("ChurnTolerantDHT");
            config.setDirectoryType("PersistentMap");
            config.setMessagingTransport("UDP");
            config.setRoutingAlgorithm("Kademlia");
            config.setRoutingStyle("Iterative");
            config.setDoUPnPNATTraversal(upnpEnable);
            config.setDoExpire(true);
            config.setDoReputOnRequester(false);
            config.setUseTimerInsteadOfThread(false);
            config.setContactPort(dhtPort);
            dht = DHTFactory.getDHT(APPLICATION_ID, APPLICATION_MAJOR_VERSION, config, null);
            dht.setHashedSecretForPut(hashedSecret);
            if (bootstrapNode != null)
                dht.joinOverlay(bootstrapNode);
            http = new HTTPServer(httpPort, dht, PROXY_SETTING, HTTP_REQUEST_TIMEOUT, cacheCallback);
            http.bind();
        } catch (Exception e) {
            logger.warn(e.getMessage(), e);
            throw e;
        }
    }

    public void stop() {
        dht.stop();
    }
}
