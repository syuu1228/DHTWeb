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

import org.slf4j.bridge.SLF4JBridgeHandler;
import ow.dht.ByteArray;
import ow.dht.DHT;
import ow.dht.DHTConfiguration;
import ow.dht.DHTFactory;
import ow.dht.ValueInfo;
import ow.id.ID;

public class DHTServer {
    static {
        SLF4JBridgeHandler.install();
    }
    public static final short APPLICATION_ID = 0x0876;
    public static final short APPLICATION_MAJOR_VERSION = 0;
    private ByteArray hashedSecret;
    private DHT<String> dht = null;

    public void initialize(String secret, boolean upnpEnable) {
        try {
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
            dht = DHTFactory.getDHT(APPLICATION_ID, APPLICATION_MAJOR_VERSION, config, null);
            dht.setHashedSecretForPut(hashedSecret);
        } catch (Exception e) {
            new ErrorDialog("Error on initialize", e.toString());
        }
    }

    public void join(String hostAndPort) {
        try {
            dht.joinOverlay(hostAndPort);
        } catch (Exception e) {
            new ErrorDialog("Error on join", e.toString());
        }
    }

    public void stop() {
        try {
            dht.stop();
        } catch (Exception e) {
            new ErrorDialog("Error on stop", e.toString());
        }
    }

    @SuppressWarnings("unchecked")
    public ValueInfo<String>[] get(String keyString) {
        ValueInfo<String>[] values = null;
        try {
            values = (ValueInfo<String>[]) dht.get(ID.getSHA1BasedID(keyString.getBytes())).toArray();
        } catch (Exception e) {
            new ErrorDialog("Error on get", e.toString());
        }
        return values;
    }

    public void put(String keyString, String value) throws Exception {
        try {
        dht.put(ID.getSHA1BasedID(keyString.getBytes()), value);
        } catch (Exception e) {
            new ErrorDialog("Error on put", e.toString());
        }
    }
}
