package com.a.eye.skywalking.network;

import com.a.eye.skywalking.network.grpc.provider.SpanStorageService;
import com.a.eye.skywalking.network.grpc.provider.TraceSearchService;
import com.a.eye.skywalking.network.listener.SpanStorageListener;
import com.a.eye.skywalking.network.listener.TraceSearchListener;
import io.grpc.Server;
import io.grpc.ServerBuilder;

import java.io.IOException;

public class ServiceProvider {
    private Server server;

    private ServiceProvider(Server server) {
        this.server = server;
    }

    public void start() throws IOException, InterruptedException {
        server.start();
        // 当JVM停止之后，Server也需要停止
        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                ServiceProvider.this.stop();
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
            serverBuilder = ServerBuilder.forPort(port);
        }

        private ServerBuilder serverBuilder;

        public ServiceProvider build() {
            return new ServiceProvider(serverBuilder.build());
        }

        public TransferServiceBuilder addSpanStorageService(SpanStorageListener spanStorageListener) {
            serverBuilder.addService(new SpanStorageService(spanStorageListener));
            return this;
        }

        public TransferServiceBuilder addTraceSearchService(TraceSearchListener traceSearchListener) {
            serverBuilder.addService(new TraceSearchService(traceSearchListener));
            return this;
        }
    }
}
