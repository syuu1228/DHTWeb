/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.dhtfox;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ow.dht.DHT;
import ow.dht.ValueInfo;
import ow.id.ID;

/**
 *
 * @author Administrator
 */
public class LocalDataMaintenanceTask implements Runnable {
    final static Logger logger = LoggerFactory.getLogger(LocalDataMaintenanceTask.class);
    private final DHT<String> dht;
    private final int port;
    public LocalDataMaintenanceTask(DHT<String> dht, int port) {
        this.dht = dht;
        this.port = port;
    }
    
    @Override
    public void run() {
        Set<ID> keySet;
        Set<ValueInfo<String>> values;
        Map<String, Boolean> checkMap = new HashMap<String, Boolean>();
        // global directory
        keySet = dht.getGlobalKeys();
        InetAddress selfAddress = dht.getSelfAddress();

        if (!keySet.isEmpty()) {
            for (ID key : keySet) {
                values = dht.getGlobalValues(key);
                if (values != null) {
                    for (ValueInfo<String> v : values) {
                        if (v.getValue().equals(selfAddress.getHostAddress() + ":" + port)) {
                            logger.info("Got self url, skipping: {}", v.getValue());
                            continue;
                        }

                        if(checkMap.get(v.getValue()) != null) {
                            if(!checkMap.get(v.getValue()))
                                try {
                                dht.removeGlobalValue(key, v);
                            } catch (Exception ex) {
                               logger.warn(ex.getMessage(), ex);
                            }
                        }
                        HttpURLConnection connection = null;
                        try {
                            URL remoteUrl = new URL("http://" + v.getValue());
                            logger.info("trying to connect {}", remoteUrl);
                            connection = (HttpURLConnection) remoteUrl.openConnection();
                            connection.connect();
                            checkMap.put(v.getValue(), true);
                        } catch (MalformedURLException ex) {
                            logger.warn(ex.getMessage(), ex);
                        } catch (IOException ex) {
                            logger.warn(ex.getMessage(), ex);
                            try {
                                checkMap.put(v.getValue(), false);
                                dht.removeGlobalValue(key, v);
                            } catch (Exception ex1) {
                               logger.warn(ex1.getMessage(), ex1);
                            }
                        } finally {
                            try {
                                connection.disconnect();
                            } catch (Exception e) {
                                logger.warn(e.getMessage(), e);
                            }
                        }
                    }
                }
            }
        }
    }
}
