package org.skywalking.apm.collector.server.grpc;

import io.grpc.netty.NettyServerBuilder;
import java.io.IOException;
import java.net.InetSocketAddress;
import org.skywalking.apm.collector.core.framework.Handler;
import org.skywalking.apm.collector.core.server.Server;
import org.skywalking.apm.collector.core.server.ServerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author pengys5
 */
public class GRPCServer implements Server {

    private final Logger logger = LoggerFactory.getLogger(GRPCServer.class);

    private final String host;
    private final int port;
    private io.grpc.Server server;
    private NettyServerBuilder nettyServerBuilder;

    public GRPCServer(String host, int port) {
        this.host = host;
        this.port = port;
    }

    @Override public String hostPort() {
        return host + ":" + port;
    }

    @Override public String serverClassify() {
        return "Google-RPC";
    }

    @Override public void initialize() throws ServerException {
        InetSocketAddress address = new InetSocketAddress(host, port);
        nettyServerBuilder = NettyServerBuilder.forAddress(address);
        server = nettyServerBuilder.build();
        logger.info("Server started, host {} listening on {}", host, port);
    }

    @Override public void start() throws ServerException {
        try {
            server.start();
        } catch (IOException e) {
            throw new GRPCServerException(e.getMessage(), e);
        }
    }

    @Override public void addHandler(Handler handler) {
    }
}
