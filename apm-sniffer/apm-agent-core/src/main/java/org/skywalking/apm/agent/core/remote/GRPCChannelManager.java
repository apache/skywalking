package org.skywalking.apm.agent.core.remote;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.internal.DnsNameResolverProvider;
import io.grpc.netty.NettyChannelBuilder;
import java.util.Random;
import org.skywalking.apm.agent.core.boot.BootService;
import org.skywalking.apm.agent.core.conf.RemoteDownstreamConfig;
import org.skywalking.apm.logging.ILog;
import org.skywalking.apm.logging.LogManager;

/**
 * @author wusheng
 */
public class GRPCChannelManager implements BootService, Runnable {
    private static final ILog logger = LogManager.getLogger(DiscoveryRestServiceClient.class);

    private volatile Thread channelManagerThread = null;
    private volatile ManagedChannel managedChannel = null;
    private Random random = new Random();

    @Override
    public void bootUp() throws Throwable {
        this.startupInBackground();
    }

    @Override
    public void afterBoot() throws Throwable {

    }

    private void startupInBackground() {
        if (channelManagerThread == null || !channelManagerThread.isAlive()) {
            synchronized (this) {
                if (channelManagerThread == null || !channelManagerThread.isAlive()) {
                    if (managedChannel == null || managedChannel.isTerminated() || managedChannel.isShutdown()) {
                        if (managedChannel != null) {
                            managedChannel.shutdownNow();
                        }
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
        while (true) {
            if (RemoteDownstreamConfig.Collector.GRPC_SERVERS.size() > 0) {
                int index = random.nextInt() % RemoteDownstreamConfig.Collector.GRPC_SERVERS.size()
                String server = RemoteDownstreamConfig.Collector.GRPC_SERVERS.get(index);
                try {
                    String[] ipAndPort = server.split(":");
                    ManagedChannelBuilder<?> channelBuilder =
                        NettyChannelBuilder.forAddress(ipAndPort[0], Integer.parseInt(ipAndPort[1]))
                            .nameResolverFactory(new DnsNameResolverProvider())
                            .maxInboundMessageSize(1024 * 1024 * 50)
                            .usePlaintext(true);
                    managedChannel = channelBuilder.build();
                    break;
                } catch (Throwable t) {
                    logger.error(t, "Create channel to {} fail.", server);
                }
            }

            int waitTime = 5 * 1000;
            logger.debug("Selected collector grpc service is not available. Wait {} seconds to try", waitTime);
            try2Sleep(waitTime);
        }
    }

    /**
     * Try to sleep, and ignore the {@link InterruptedException}
     *
     * @param millis the length of time to sleep in milliseconds
     */
    private void try2Sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {

        }
    }
}
