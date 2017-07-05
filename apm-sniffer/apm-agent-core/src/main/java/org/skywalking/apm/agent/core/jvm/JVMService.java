package org.skywalking.apm.agent.core.jvm;

import io.grpc.ManagedChannel;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import org.skywalking.apm.agent.core.boot.BootService;
import org.skywalking.apm.agent.core.boot.ServiceManager;
import org.skywalking.apm.agent.core.conf.RemoteDownstreamConfig;
import org.skywalking.apm.agent.core.dictionary.DictionaryUtil;
import org.skywalking.apm.agent.core.jvm.cpu.CPUProvider;
import org.skywalking.apm.agent.core.jvm.gc.GCProvider;
import org.skywalking.apm.agent.core.jvm.memory.MemoryProvider;
import org.skywalking.apm.agent.core.jvm.memorypool.MemoryPoolProvider;
import org.skywalking.apm.agent.core.remote.GRPCChannelListener;
import org.skywalking.apm.agent.core.remote.GRPCChannelManager;
import org.skywalking.apm.agent.core.remote.GRPCChannelStatus;
import org.skywalking.apm.logging.ILog;
import org.skywalking.apm.logging.LogManager;
import org.skywalking.apm.network.proto.JVMMetric;
import org.skywalking.apm.network.proto.JVMMetrics;
import org.skywalking.apm.network.proto.JVMMetricsServiceGrpc;

import static org.skywalking.apm.agent.core.remote.GRPCChannelStatus.CONNECTED;

/**
 * @author wusheng
 */
public class JVMService implements BootService, Runnable {
    private static final ILog logger = LogManager.getLogger(JVMService.class);
    private ReentrantLock lock = new ReentrantLock();
    private volatile LinkedList<JVMMetric> buffer = new LinkedList<JVMMetric>();
    private SimpleDateFormat sdf = new SimpleDateFormat("ss");
    private volatile ScheduledFuture<?> collectMetricFuture;
    private volatile ScheduledFuture<?> sendMetricFuture;
    private volatile int lastBlockIdx = -1;

    @Override
    public void beforeBoot() throws Throwable {

    }

    @Override
    public void boot() throws Throwable {
        collectMetricFuture = Executors
            .newSingleThreadScheduledExecutor()
            .scheduleAtFixedRate(this, 0, 1, TimeUnit.SECONDS);
        sendMetricFuture = Executors
            .newSingleThreadScheduledExecutor()
            .scheduleAtFixedRate(new Sender(), 0, 15, TimeUnit.SECONDS);
    }

    @Override
    public void afterBoot() throws Throwable {

    }

    @Override
    public void run() {
        if (RemoteDownstreamConfig.Agent.APPLICATION_ID != DictionaryUtil.nullValue()
            && RemoteDownstreamConfig.Agent.APPLICATION_INSTANCE_ID != DictionaryUtil.nullValue()
            ) {
            long currentTimeMillis = System.currentTimeMillis();
            Date day = new Date(currentTimeMillis);
            String second = sdf.format(day);
            int blockIndex = Integer.parseInt(second) / 15;
            if (blockIndex != lastBlockIdx) {
                lastBlockIdx = blockIndex;
                try {
                    JVMMetric.Builder jvmBuilder = JVMMetric.newBuilder();
                    jvmBuilder.setTime(currentTimeMillis);
                    jvmBuilder.setCpu(CPUProvider.INSTANCE.getCpuMetric());
                    jvmBuilder.addAllMemory(MemoryProvider.INSTANCE.getMemoryMetricList());
                    jvmBuilder.addAllMemoryPool(MemoryPoolProvider.INSTANCE.getMemoryPoolMetricList());
                    jvmBuilder.addAllGc(GCProvider.INSTANCE.getGCList());

                    JVMMetric jvmMetric = jvmBuilder.build();
                    lock.lock();
                    try {
                        buffer.add(jvmMetric);
                        while (buffer.size() > 4) {
                            buffer.removeFirst();
                        }
                    } finally {
                        lock.unlock();
                    }
                } catch (Exception e) {
                    logger.error(e, "Collect JVM info fail.");
                }
            }
        }
    }

    private class Sender implements Runnable, GRPCChannelListener {
        private volatile GRPCChannelStatus status = GRPCChannelStatus.DISCONNECT;
        private volatile JVMMetricsServiceGrpc.JVMMetricsServiceBlockingStub stub = null;

        @Override
        public void run() {
            if (RemoteDownstreamConfig.Agent.APPLICATION_ID != DictionaryUtil.nullValue()
                && RemoteDownstreamConfig.Agent.APPLICATION_INSTANCE_ID != DictionaryUtil.nullValue()
                ) {
                if (status == GRPCChannelStatus.CONNECTED) {
                    try {
                        JVMMetrics.Builder builder = JVMMetrics.newBuilder();
                        lock.lock();
                        try {
                            builder.addAllMetrics(buffer);
                            buffer.clear();
                        } finally {
                            lock.unlock();
                        }

                        builder.setApplicationInstanceId(RemoteDownstreamConfig.Agent.APPLICATION_INSTANCE_ID);
                        stub.collect(builder.build());
                    } catch (Throwable t) {
                        logger.error(t, "send JVM metrics to Collector fail.");
                    }
                }
            }
        }

        @Override
        public void statusChanged(GRPCChannelStatus status) {
            this.status = status;
            if (CONNECTED.equals(status)) {
                ManagedChannel channel = ServiceManager.INSTANCE.findService(GRPCChannelManager.class).getManagedChannel();
                stub = JVMMetricsServiceGrpc.newBlockingStub(channel);
            }
        }
    }
}
