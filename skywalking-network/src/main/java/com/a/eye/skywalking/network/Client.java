package com.a.eye.skywalking.network;

import io.grpc.Channel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.internal.DnsNameResolverProvider;

public class Client {

    private static Client  client;
    private        Channel channel;

    private static final String DEFAULT_ADDRESS = "localhost";
    private static final int    DEFAULT_PORT    = 34000;

    private String host = DEFAULT_ADDRESS;
    private int    port = DEFAULT_PORT;

    public static Client forAddress(String host, int port) {
        if (client == null) {
            client = new Client(host, port);
        }

        return client;
    }

    public static Client INSTANCE() {
        return client;
    }

    private Client(String host, int port) {
        this.host = host;
        this.port = port;
    }


    public void start() {
        ManagedChannelBuilder<?> channelBuilder =
                ManagedChannelBuilder.forAddress(host, port).nameResolverFactory(new DnsNameResolverProvider())
                        .usePlaintext(true);
        channel = channelBuilder.build();
    }
}
