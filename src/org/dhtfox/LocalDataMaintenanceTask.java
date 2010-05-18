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
	private final InetAddress selfAddress;
	
    public LocalDataMaintenanceTask(DHT<String> dht, int port, InetAddress selfAddress) {
        this.dht = dht;
        this.port = port;
        this.selfAddress = selfAddress;
    }
    
    @Override
    public void run() {
        Set<ID> keySet;
        Set<ValueInfo<String>> values;
        Map<String, Boolean> checkMap = new HashMap<String, Boolean>();
        // global directory
        keySet = dht.getGlobalKeys();

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
