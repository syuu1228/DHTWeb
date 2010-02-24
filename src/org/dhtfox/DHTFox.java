package org.dhtfox;

import java.net.UnknownHostException;
import java.util.Set;

import ow.dht.ByteArray;
import ow.dht.DHT;
import ow.dht.DHTConfiguration;
import ow.dht.DHTFactory;
import ow.dht.ValueInfo;
import ow.id.ID;
import ow.routing.RoutingException;

public class DHTFox {
	public static final short APPLICATION_ID = 0x0876;
	public static final short APPLICATION_MAJOR_VERSION = 0;
	private ByteArray hashedSecret;
	DHT<String> dht = null;

	public DHTFox(String secret, boolean upnpEnable) {
		try {
			this.hashedSecret = new ByteArray(secret.getBytes("UTF-8"))
					.hashWithSHA1();
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

			dht = DHTFactory.getDHT(APPLICATION_ID, APPLICATION_MAJOR_VERSION,
					config, null);
			dht.setHashedSecretForPut(hashedSecret);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public void join(String hostAndPort) throws UnknownHostException,
			RoutingException {
		dht.joinOverlay(hostAndPort);
	}

	public void stop() {
		dht.stop();
	}

	public Set<ValueInfo<String>> get(String keyString) throws RoutingException {
		return dht.get(ID.getSHA1BasedID(keyString.getBytes()));
	}

	public void put(String keyString, String value) throws Exception {
		dht.put(ID.getSHA1BasedID(keyString.getBytes()), value);
	}
}
