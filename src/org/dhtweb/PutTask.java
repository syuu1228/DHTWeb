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
package org.dhtweb;

import java.net.InetAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ow.dht.DHT;
import ow.id.ID;

/**
 *
 * @author Administrator
 */
public class PutTask implements Runnable {
    public static final Logger logger = LoggerFactory.getLogger(PutTask.class);
    public static final Logger putLogger = LoggerFactory.getLogger("putlog");
	private static final List<ID> puttedList = new ArrayList<ID>();
    private final ID key;
    private final DHT<String> dht;
    private final int port;
    private final InetAddress selfAddress;
	private URI uri;

    public PutTask(DHT<String> dht, int port, ID key, InetAddress selfAddress, URI uri) {
        this.dht = dht;
        this.port = port;
        this.key = key;
        this.selfAddress = selfAddress;
        this.uri = uri;
    }

    @Override
    public void run() {
    	if (puttedList.contains(key))
    		return;
    	puttedList.add(key);
        logger.info("key:{} selfAddress:{}", key, selfAddress.getHostAddress());
		long currentTime = System.currentTimeMillis();
		int i = 0;
        while(true) {
            try {
                dht.put(key, selfAddress.getHostAddress() + ":" + port);
                logger.info("putted key:{} selfAddress:{}", key, selfAddress.getHostAddress());
                break;
            } catch (Exception e) {
            	i++;
                logger.warn(e.getMessage(), e);
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ex) {
                }
            }
        }
        putLogger.info(uri + "," + (System.currentTimeMillis() - currentTime) + "," + i);
    }
}
