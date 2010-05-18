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

import java.net.InetAddress;
import java.net.UnknownHostException;

import ow.messaging.upnp.Mapping;
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
	
	public void addMapping(Mapping map) {
		manager.addMapping(map);
	}
	
	public void deleteMapping(Mapping map) {
		manager.deleteMapping(map);
	}
}
