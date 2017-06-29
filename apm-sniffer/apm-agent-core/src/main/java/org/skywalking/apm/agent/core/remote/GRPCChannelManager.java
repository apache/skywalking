package org.skywalking.apm.agent.core.remote;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.internal.DnsNameResolverProvider;
import io.grpc.netty.NettyChannelBuilder;
import org.skywalking.apm.agent.core.boot.BootService;
import org.skywalking.apm.logging.ILog;
import org.skywalking.apm.logging.LogManager;

/**
 * @author wusheng
 */
public class GRPCChannelManager implements BootService, Runnable {
    private static final ILog logger = LogManager.getLogger(DiscoveryRestServiceClient.class);

    private volatile Thread channelManagerThread = null;
    private volatile ManagedChannel managedChannel = null;

    @Override
    public void bootUp() throws Throwable {
        this.startupInBackground();
    }

    private void startupInBackground() {
        if (channelManagerThread == null || !channelManagerThread.isAlive()) {
            synchronized (this) {
                if (channelManagerThread == null || !channelManagerThread.isAlive()) {
                    if (managedChannel == null || managedChannel.isTerminated() || managedChannel.isShutdown()) {
                        managedChannel.shutdownNow();
                        Thread channelManagerThread = new Thread(this, "ChannelManagerThread");
                        channelManagerThread.setDaemon(true);
                        channelManagerThread.start();
                    }
                }
            }
        }
    }

    @Override
    public void run() {
        ManagedChannelBuilder<?> channelBuilder =
            NettyChannelBuilder.forAddress("127.0.0.1", 808)
                .nameResolverFactory(new DnsNameResolverProvider())
                .maxInboundMessageSize(1024 * 1024 * 50)
                .usePlaintext(true);
        managedChannel = channelBuilder.build();
    }

    public static void main(String[] args) throws Throwable {
        new GRPCChannelManager().bootUp();

        Thread.sleep(60 * 1000);
    }
}
