package org.skywalking.apm.collector.rpc;

/**
 * @author pengys5
 */
public class RPCAddress {
    private final String address;
    private final int port;

    public RPCAddress(String address, int port) {
        this.address = address;
        this.port = port;
    }

    public String getAddress() {
        return address;
    }

    public int getPort() {
        return port;
    }
}
