package org.skywalking.apm.collector.core.cluster;

import java.util.LinkedList;
import java.util.List;
import org.skywalking.apm.collector.core.framework.Listener;

/**
 * @author pengys5
 */
public abstract class ClusterDataListener implements Listener {

    private List<String> addresses;

    public ClusterDataListener() {
        addresses = new LinkedList<>();
    }

    public abstract String path();

    public final void addAddress(String address) {
        addresses.add(address);
    }

    public final List<String> getAddresses() {
        return addresses;
    }

    public final void clearData() {
        addresses.clear();
    }
}
