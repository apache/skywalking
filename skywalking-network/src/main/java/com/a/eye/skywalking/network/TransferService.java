package com.a.eye.skywalking.network;

import com.a.eye.skywalking.network.grpc.services.SpanStorageService;
import com.a.eye.skywalking.network.grpc.services.TraceSearchService;
import com.a.eye.skywalking.network.listener.SpanStorageNotifier;
import com.a.eye.skywalking.network.listener.TraceSearchNotifier;
import io.grpc.Server;
import io.grpc.ServerBuilder;

import java.io.IOException;

public class TransferService {
    private static final int DEFAULT_SERVICE_PORT = 34000;

    private Server server;

    private TransferService(Server server) {
        this.server = server;
    }

    public void start() throws IOException, InterruptedException {
        server.start();
        // 当JVM停止之后，Server也需要停止
        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                TransferService.this.stop();
            }
        });

        blockUntilShutdown();
    }

    private void stop() {
        if (server != null) {
            server.shutdown();
        }
    }

    private void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }

    public static class TransferServiceBuilder {
        private TransferServiceBuilder(int port) {
            if (port < 0 && port > 65535) {
                port = DEFAULT_SERVICE_PORT;
            }

            serverBuilder = ServerBuilder.forPort(port);
        }

        private ServerBuilder serverBuilder;

        public static TransferServiceBuilder newBuilder(int port) {
            return new TransferServiceBuilder(port);
        }

        public TransferService build() {
            return new TransferService(serverBuilder.build());
        }

        public TransferServiceBuilder startSpanStorageService(SpanStorageNotifier spanStorageListener) {
            serverBuilder.addService(new SpanStorageService(spanStorageListener));
            return this;
        }

        public TransferServiceBuilder startTraceSearchService(TraceSearchNotifier traceSearchNotifier) {
            serverBuilder.addService(new TraceSearchService(traceSearchNotifier));
            return this;
        }
    }
}
