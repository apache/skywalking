package org.skywalking.apm.collector.server.grpc;

import io.grpc.netty.NettyServerBuilder;
import java.io.IOException;
import java.net.InetSocketAddress;
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

    public GRPCServer(String host, int port) {
        this.host = host;
        this.port = port;
    }

    @Override public void initialize() throws ServerException {
        InetSocketAddress address = new InetSocketAddress(host, port);
        NettyServerBuilder nettyServerBuilder = NettyServerBuilder.forAddress(address);
        try {
            io.grpc.Server server = nettyServerBuilder.build().start();
            blockUntilShutdown(server);
        } catch (InterruptedException | IOException e) {
            throw new GRPCServerException(e.getMessage(), e);
        }
        logger.info("Server started, host {} listening on {}", host, port);
    }

    private void blockUntilShutdown(io.grpc.Server server) throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }
}
