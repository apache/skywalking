package org.skywalking.apm.collector.client.grpc;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.skywalking.apm.collector.core.client.Client;
import org.skywalking.apm.collector.core.client.ClientException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author pengys5
 */
public class GRPCClient implements Client {

    private final Logger logger = LoggerFactory.getLogger(GRPCClient.class);

    private final String host;

    private final int port;

    private ManagedChannel channel;

    public GRPCClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    @Override public void initialize() throws ClientException {
        channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext(true).build();
    }

    public ManagedChannel getChannel() {
        return channel;
    }
}
