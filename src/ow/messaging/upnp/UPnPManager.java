/*
 * Copyright 2007,2009 Kazuyuki Shudo, and contributors.
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
package ow.messaging.upnp;

import fr.free.miniupnp.IGDdatas;
import fr.free.miniupnp.MiniupnpcLibrary;
import fr.free.miniupnp.UPNPDev;
import fr.free.miniupnp.UPNPUrls;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * An utility to establish address and port mappings on an Internet router
 * with UPnP protocol.
 */
public final class UPnPManager {

    private final static Logger logger = Logger.getLogger("messaging");
    private final static int UPNP_DELAY = 2000;
    private static UPnPManager singletonInstance = new UPnPManager();

    public static UPnPManager getInstance() {
        return UPnPManager.singletonInstance;
    }
    private MiniupnpcLibrary miniupnpc = MiniupnpcLibrary.INSTANCE;
    private UPNPDev devlist = null;
    private UPNPUrls urls = new UPNPUrls();
    private IGDdatas data = new IGDdatas();
    private ByteBuffer lanaddr = ByteBuffer.allocate(16);
    private final Set<Mapping> registeredMappings = new HashSet<Mapping>();
    private boolean deviceFound = false;
    private DeviceDiscoverThread discover;

    private class DeviceDiscoverThread extends Thread {
        public void run() {
            devlist = miniupnpc.upnpDiscover(UPNP_DELAY, (String) null, (String) null, 0);
            if (devlist != null) {
                int i;
                logger.log(Level.INFO, "List of UPNP devices found on the network :");
                for (UPNPDev device = devlist; device != null; device = device.pNext) {
                    logger.log(Level.INFO, "desc: " + device.descURL.getString(0) + " st: " + device.st.getString(0));
                }
                if ((i = miniupnpc.UPNP_GetValidIGD(devlist, urls, data, lanaddr, 16)) != 0) {
                    switch (i) {
                        case 1:
                            logger.log(Level.INFO, "Found valid IGD : " + urls.controlURL.getString(0));
                            break;
                        case 2:
                            logger.log(Level.INFO, "Found a (not connected?) IGD : " + urls.controlURL.getString(0));
                            logger.log(Level.INFO, "Trying to continue anyway");
                            break;
                        case 3:
                            logger.log(Level.INFO, "UPnP device found. Is it an IGD ? : " + urls.controlURL.getString(0));
                            logger.log(Level.INFO, "Trying to continue anyway");
                            break;
                        default:
                            logger.log(Level.INFO, "Found device (igd ?) : " + urls.controlURL.getString(0));
                            logger.log(Level.INFO, "Trying to continue anyway");

                    }
                    logger.log(Level.INFO, "Local LAN ip address : " + new String(lanaddr.array()));
                } else {
                    logger.log(Level.SEVERE, "No valid UPNP Internet Gateway Device found.");
                    return;
                }
            } else {
                logger.log(Level.SEVERE, "No IGD UPnP Device found on the network !\n");
                return;
            }
            deviceFound = true;
            synchronized (UPnPManager.this) {
                UPnPManager.this.notifyAll();
            }
        }
    }

    private UPnPManager() {
    }

    public boolean start() {
        discover = new DeviceDiscoverThread();
        discover.start();
        logger.log(Level.INFO, "UPnP manager started.");
        return true;
    }

    public void stop() {
    }

    public void waitForDeviceFound() {
        this.waitForDeviceFound(Long.MAX_VALUE);
    }

    public boolean waitForDeviceFound(long timeout) {
        synchronized (this) {
            if (deviceFound) {
                return true;
            }
            try {
                this.wait(timeout);
            } catch (InterruptedException e) {
            }
        }
        return deviceFound;
    }

    public InetAddress getExternalAddress() {
        try {
            ByteBuffer externalAddress = ByteBuffer.allocate(16);
            miniupnpc.UPNP_GetExternalIPAddress(urls.controlURL.getString(0),
                    new String(data.servicetype), externalAddress);
            return InetAddress.getByName(new String(externalAddress.array()));
        } catch (UnknownHostException ex) {
            Logger.getLogger(UPnPManager.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }

    /**
     * Adds a port mapping.
     *
     * @param protocol "TCP" or "UDP"
     * @param description optional
     * @return true if succeed.
     */
    public boolean addMapping(Mapping map) {
        ByteBuffer intClient = ByteBuffer.allocate(16);
        ByteBuffer intPort = ByteBuffer.allocate(6);
        boolean succeed = false;
        int ret = miniupnpc.UPNP_AddPortMapping(
                urls.controlURL.getString(0), new String(data.servicetype),
                Integer.toString(map.getExternalPort()),
                Integer.toString(map.getInternalPort()),
                new String(lanaddr.array()), map.getDescription(),
                map.getProtocol().toString(), null);
        if (ret == MiniupnpcLibrary.UPNPCOMMAND_SUCCESS) {
            ret = miniupnpc.UPNP_GetSpecificPortMappingEntry(
                    urls.controlURL.getString(0), new String(data.servicetype),
                    Integer.toString(map.getExternalPort()),
                    map.getProtocol().toString(), intClient, intPort);
            if (ret == MiniupnpcLibrary.UPNPCOMMAND_SUCCESS)
                succeed = true;
            else
                System.out.println("GetSpecificPortMappingEntry() failed with code " + ret);
        }else{
            System.out.println("AddPortMapping() failed with code " + ret);
          }
        System.out.println("InternalIP:Port = "
                + new String(intClient.array()) + ":" + new String(intPort.array()));

        if (succeed) {
            synchronized (this.registeredMappings) {
                this.registeredMappings.add(map);
            }
        }
        logger.log(Level.INFO, "UPnP address port mapping "
                + (succeed ? "succeeded" : "failed")
                + ": ext port " + map.getExternalPort()
                + ", internal port " + map.getInternalPort());

        return succeed;
    }

    /**
     * Deletes a port mapping.
     *
     * @return true if succeed.
     */
    public boolean deleteMapping(int externalPort, Mapping.Protocol protocol) {
        Mapping map = new Mapping(externalPort, null, 0, protocol, null);
        return this.deleteMapping(map);
    }

    /**
     * Deletes a port mapping.
     *
     * @param protocol "TCP" or "UDP"
     * @return true if succeed.
     */
    public boolean deleteMapping(Mapping map) {
        int ret = miniupnpc.UPNP_DeletePortMapping(
                urls.controlURL.getString(0),
                new String(data.servicetype),
                Integer.toString(map.getExternalPort()),
                map.getProtocol().toString(), null);
        boolean succeed = ret == miniupnpc.UPNPCOMMAND_SUCCESS ? true : false;
        if (succeed) {
            synchronized (this.registeredMappings) {
                this.registeredMappings.remove(map);
            }
        }
        logger.log(Level.INFO, "UPnP address port mapping "
                + (succeed ? "deleted" : "deletion failed")
                + ": ext port " + map.getExternalPort());

        return succeed;
    }

    public void clearMapping() {
        int size;
        Mapping[] maps;

        synchronized (this.registeredMappings) {
            size = this.registeredMappings.size();
            if (size <= 0) {
                return;
            }

            maps = new Mapping[size];
            this.registeredMappings.toArray(maps);
        }

        for (Mapping m : maps) {
            this.deleteMapping(m.getExternalPort(), m.getProtocol());
        }
    }
}
