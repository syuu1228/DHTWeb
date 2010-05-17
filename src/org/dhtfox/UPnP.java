package org.dhtfox;

import java.net.InetAddress;
import java.net.UnknownHostException;

import ow.messaging.upnp.UPnPManager;

public class UPnP {
	private UPnPManager manager;
	private boolean enable;
	
	public UPnP(boolean enable) {
		this.enable = enable;
		if (enable)
			manager = UPnPManager.getInstance();
	}
	
	public InetAddress getSelfAddress() {
		if (!enable)
			try {
				return InetAddress.getLocalHost();
			} catch (UnknownHostException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				return null;
			}
		return manager.getExternalAddress();
	}
}
