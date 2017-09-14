package org.skywalking.apm.agent.core.remote;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.internal.DnsNameResolverProvider;
import io.grpc.netty.NettyChannelBuilder;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.skywalking.apm.agent.core.boot.BootService;
import org.skywalking.apm.agent.core.conf.RemoteDownstreamConfig;
import org.skywalking.apm.logging.ILog;
import org.skywalking.apm.logging.LogManager;

import static org.skywalking.apm.agent.core.conf.Config.Collector.GRPC_CHANNEL_CHECK_INTERVAL;

/**
 * @author wusheng
 */
public class GRPCChannelManager implements BootService, Runnable {
    private static final ILog logger = LogManager.getLogger(GRPCChannelManager.class);

    private volatile ManagedChannel managedChannel = null;
    private volatile ScheduledFuture<?> connectCheckFuture;
    private volatile boolean reconnect = true;
    private Random random = new Random();
    private List<GRPCChannelListener> listeners = Collections.synchronizedList(new LinkedList<GRPCChannelListener>());

    @Override
    public void beforeBoot() throws Throwable {

    }

    @Override
    public void boot() throws Throwable {
        connectCheckFuture = Executors
            .newSingleThreadScheduledExecutor()
            .scheduleAtFixedRate(this, 0, GRPC_CHANNEL_CHECK_INTERVAL, TimeUnit.SECONDS);
    }

    @Override
    public void afterBoot() throws Throwable {

    }

    @Override
    public void shutdown() throws Throwable {
        connectCheckFuture.cancel(true);
        managedChannel.shutdownNow();
    }

    @Override
    public void run() {
        if (reconnect) {
            if (RemoteDownstreamConfig.Collector.GRPC_SERVERS.size() > 0) {
                int index = random.nextInt() % RemoteDownstreamConfig.Collector.GRPC_SERVERS.size();
                String server = RemoteDownstreamConfig.Collector.GRPC_SERVERS.get(index);
                try {
                    String[] ipAndPort = server.split(":");
                    ManagedChannelBuilder<?> channelBuilder =
                        NettyChannelBuilder.forAddress(ipAndPort[0], Integer.parseInt(ipAndPort[1]))
                            .nameResolverFactory(new DnsNameResolverProvider())
                            .maxInboundMessageSize(1024 * 1024 * 50)
                            .usePlaintext(true);
                    managedChannel = channelBuilder.build();
                    if (!managedChannel.isShutdown() && !managedChannel.isTerminated()) {
                        reconnect = false;
                        notify(GRPCChannelStatus.CONNECTED);
                    } else {
                        notify(GRPCChannelStatus.DISCONNECT);
                    }
                    return;
                } catch (Throwable t) {
                    logger.error(t, "Create channel to {} fail.", server);
                    notify(GRPCChannelStatus.DISCONNECT);
                }
            }

            logger.debug("Selected collector grpc service is not available. Wait {} seconds to retry", GRPC_CHANNEL_CHECK_INTERVAL);
        }
    }

    public void addChannelListener(GRPCChannelListener listener) {
        listeners.add(listener);
    }

    public ManagedChannel getManagedChannel() {
        return managedChannel;
    }

    /**
     * If the given expcetion is triggered by network problem, connect in background.
     *
     * @param throwable
     */
    public void reportError(Throwable throwable) {
        if (isNetworkError(throwable)) {
            reconnect = true;
        }
    }

    private void notify(GRPCChannelStatus status) {
        for (GRPCChannelListener listener : listeners) {
            try {
                listener.statusChanged(status);
            } catch (Throwable t) {
                logger.error(t, "Fail to notify {} about channel connected.", listener.getClass().getName());
            }
        }
    }

    private boolean isNetworkError(Throwable throwable) {
        if (throwable instanceof StatusRuntimeException) {
            StatusRuntimeException statusRuntimeException = (StatusRuntimeException)throwable;
            return statusEquals(statusRuntimeException.getStatus(),
                Status.UNAVAILABLE,
                Status.PERMISSION_DENIED,
                Status.UNAUTHENTICATED,
                Status.RESOURCE_EXHAUSTED,
                Status.UNKNOWN
            );
        }
        return false;
    }

    private boolean statusEquals(Status sourceStatus, Status... potentialStatus) {
        for (Status status : potentialStatus) {
            if (sourceStatus.getCode() == status.getCode()) {
                return true;
            }
        }
        return false;
    }
}
