package com.a.eye.skywalking.network;

import com.a.eye.skywalking.network.grpc.server.AsyncTraceSearchServer;
import com.a.eye.skywalking.network.grpc.server.SpanStorageServer;
import com.a.eye.skywalking.network.grpc.server.TraceSearchServer;
import com.a.eye.skywalking.network.listener.server.SpanStorageServerListener;
import com.a.eye.skywalking.network.listener.server.TraceSearchListener;
import io.grpc.netty.NettyServerBuilder;
import io.netty.channel.nio.NioEventLoopGroup;

import java.io.IOException;

public class Server {
    private io.grpc.Server server;

    private Server(io.grpc.Server server) {
        this.server = server;
    }

    public void start() throws IOException, InterruptedException {
        server.start();
        // 当JVM停止之后，Server也需要停止
        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                Server.this.stop();
            }
        });
    }

    public void stop() {
        if (server != null) {
            server.shutdown();
        }
    }

    public static TransferServiceBuilder newBuilder(int port) {
        return new TransferServiceBuilder(port);
    }

    public static class TransferServiceBuilder {
        private TransferServiceBuilder(int port) {
            serverBuilder = NettyServerBuilder.forPort(port);
            serverBuilder.maxConcurrentCallsPerConnection(4);
        }

        private NettyServerBuilder serverBuilder;

        public Server build() {
            return new Server(serverBuilder.bossEventLoopGroup(new NioEventLoopGroup(1))
                    .workerEventLoopGroup(new NioEventLoopGroup()).build());
        }

        public TransferServiceBuilder addSpanStorageService(SpanStorageServerListener spanStorageServerListener) {
            serverBuilder.addService(new SpanStorageServer(spanStorageServerListener));
            return this;
        }

        public TransferServiceBuilder addTraceSearchService(TraceSearchListener traceSearchListener) {
            serverBuilder.addService(new TraceSearchServer(traceSearchListener));
            return this;
        }

        public TransferServiceBuilder addAsyncTraceSearchService(TraceSearchListener traceSearchListener){
            serverBuilder.addService(new AsyncTraceSearchServer(traceSearchListener));
            return this;
        }
    }
}
