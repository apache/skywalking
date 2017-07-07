package org.skywalking.apm.collector.rpc;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author pengys5
 */
public class RPCAddressContext {

    private Map<String, RPCAddress> rpcAddresses = new ConcurrentHashMap<>();

    public Collection<RPCAddress> rpcAddressCollection() {
        return rpcAddresses.values();
    }

    public void putAddress(String ownerAddress, RPCAddress rpcAddress) {
        rpcAddresses.put(ownerAddress, rpcAddress);
    }

    public void removeAddress(String ownerAddress) {
        rpcAddresses.remove(ownerAddress);
    }
}
