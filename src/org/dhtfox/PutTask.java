/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.dhtfox;

import java.net.InetAddress;
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

    public PutTask(DHT<String> dht, int port, ID key, InetAddress selfAddress) {
        this.dht = dht;
        this.port = port;
        this.key = key;
        this.selfAddress = selfAddress;
    }

    @Override
    public void run() {
    	if (puttedList.contains(key))
    		return;
    	puttedList.add(key);
        logger.info("key:{} selfAddress:{}", key, selfAddress.getHostAddress());
        putLogger.info("start key:{}", key);
		long currentTime = System.currentTimeMillis();
        while(true) {
            try {
                dht.put(key, selfAddress.getHostAddress() + ":" + port);
                logger.info("putted key:{} selfAddress:{}", key, selfAddress.getHostAddress());
                break;
            } catch (Exception e) {
                logger.warn(e.getMessage(), e);
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ex) {
                }
            }
        }
        putLogger.info("end key:{} time:{}", key, 
						System.currentTimeMillis() - currentTime);
    }
}
