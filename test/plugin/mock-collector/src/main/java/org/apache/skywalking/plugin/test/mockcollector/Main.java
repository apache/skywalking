package org.apache.skywalking.plugin.test.mockcollector;

import io.grpc.netty.NettyServerBuilder;
import io.netty.channel.local.LocalAddress;

import java.net.InetSocketAddress;

import org.apache.skywalking.plugin.test.mockcollector.service.*;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;

public class Main {
    public static void main(String[] args) throws Exception {
        NettyServerBuilder.forAddress(LocalAddress.ANY).forPort(19876)
                .maxConcurrentCallsPerConnection(12).maxMessageSize(16777216)
                .addService(new MockRegisterService())
                .addService(new MockInstancePingService())
                .addService(new MockJVMMetricsService())
                .addService(new MockTraceSegmentV1Service())
                .addService(new MockApplicationRegisterService())
                .addService(new MockInstanceDiscoveryService())
                .addService(new MockJVMMetricsV1Service())
                .addService(new MockNetworkAddressRegisterService())
                .addService(new MockServiceNameDiscoveryService())
                .addService(new MockTraceSegmentService())
                .addService(new MockJVMMetricReportService())
                .build().start();

        Server jettyServer = new Server(new InetSocketAddress("0.0.0.0",
                Integer.valueOf(12800)));
        String contextPath = "/";
        ServletContextHandler servletContextHandler = new ServletContextHandler(ServletContextHandler.NO_SESSIONS);
        servletContextHandler.setContextPath(contextPath);
        servletContextHandler.addServlet(GrpcAddressHttpService.class, GrpcAddressHttpService.SERVLET_PATH);
        servletContextHandler.addServlet(ReceiveDataService.class, ReceiveDataService.SERVLET_PATH);
        servletContextHandler.addServlet(ClearReceiveDataService.class, ClearReceiveDataService.SERVLET_PATH);
        jettyServer.setHandler(servletContextHandler);
        jettyServer.start();
    }
}
