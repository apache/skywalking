package org.skywalking.apm.collector.core.cluster;

import java.util.HashSet;
import java.util.Set;
import org.skywalking.apm.collector.core.framework.Listener;

/**
 * @author pengys5
 */
public abstract class ClusterDataListener implements Listener {

    private Set<String> addresses;

    public ClusterDataListener() {
        addresses = new HashSet<>();
    }

    public abstract String path();

    public final void addAddress(String address) {
        addresses.add(address);
    }

    public final void removeAddress(String address) {
        addresses.remove(address);
    }

    public final Set<String> getAddresses() {
        return addresses;
    }

    public abstract void serverJoinNotify(String serverAddress);

    public abstract void serverQuitNotify(String serverAddress);
}
