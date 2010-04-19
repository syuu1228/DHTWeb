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

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import netscape.javascript.JSObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;
import ow.dht.ByteArray;
import ow.dht.DHT;
import ow.dht.DHTConfiguration;
import ow.dht.DHTFactory;
import ow.id.ID;
import ow.messaging.upnp.Mapping;
import ow.messaging.upnp.UPnPManager;
import ow.tool.dhtshell.DHTShell;

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
    private boolean upnpEnable;
    private Mapping httpMapping;
    private ExecutorService putExecutor = Executors.newSingleThreadExecutor();
    private ScheduledExecutorService maintenanceExecutor = Executors.newScheduledThreadPool(1);

    public DHT<String> getDHT() {
        return dht;
    }

    public boolean start(String secret, boolean upnpEnable, String bootstrapNode, int dhtPort, int httpPort, JSObject cacheCallback, JSObject loggerCallback, String logbackFilePath) {
        this.upnpEnable = upnpEnable;
        try {
            LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
            JoranConfigurator configurator = new JoranConfigurator();
            configurator.setContext(lc);
            lc.reset();
            configurator.doConfigure(logbackFilePath);
        } catch (Exception e) {
            loggerCallback.call("log", new Object[]{e.getMessage()});
            return false;
        }
        try {
            this.hashedSecret = new ByteArray(secret.getBytes("UTF-8")).hashWithSHA1();
            DHTConfiguration config = DHTFactory.getDefaultConfiguration();
            /*
            config.setImplementationName("ChurnTolerantDHT");
            config.setDirectoryType("PersistentMap");
            config.setMessagingTransport("UDP");
            config.setRoutingAlgorithm("Kademlia");
            config.setRoutingStyle("Iterative");
            config.setDoExpire(true);
            config.setDoReputOnRequester(false);
            config.setUseTimerInsteadOfThread(false);
             */
            config.setDoUPnPNATTraversal(upnpEnable);
            config.setContactPort(dhtPort);
            dht = DHTFactory.getDHT(APPLICATION_ID, APPLICATION_MAJOR_VERSION, config, null);
//            dht.setHashedSecretForPut(hashedSecret);
            if (bootstrapNode != null) {
                dht.joinOverlay(bootstrapNode);
            }
            DHTShell shell = new DHTShell();
            shell.init(dht, 9999);

            maintenanceExecutor.scheduleAtFixedRate(new LocalDataMaintenanceTask(dht, httpPort), 60, 60, TimeUnit.SECONDS);

            http = new HTTPServer(httpPort, dht, PROXY_SETTING, HTTP_REQUEST_TIMEOUT, cacheCallback, putExecutor);
            http.bind();
            if (upnpEnable) {
                UPnPManager upnp = UPnPManager.getInstance();
                httpMapping = new Mapping(httpPort, null, httpPort, Mapping.Protocol.TCP, "DHTFox httpd");
                upnp.addMapping(httpMapping);
            }
            return true;
        } catch (Exception e) {
            logger.warn(e.getMessage(), e);
            return false;
        }
    }

    public void stop() {
        dht.stop();
        http.stop();
        if (upnpEnable) {
            UPnPManager upnp = UPnPManager.getInstance();
            upnp.deleteMapping(httpMapping);
        }
    }

    public void registerCache(String u) throws MalformedURLException {
        URL url = new URL(new URL(u).getPath().replaceFirst("^/proxy/", ""));
        ID key = ID.getSHA1BasedID(url.toString().getBytes());
        putExecutor.submit(new PutTask(dht, url.getPort(), key));
    }
}
