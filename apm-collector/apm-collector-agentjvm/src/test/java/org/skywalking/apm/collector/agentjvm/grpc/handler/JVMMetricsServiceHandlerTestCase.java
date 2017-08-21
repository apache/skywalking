package org.skywalking.apm.collector.agentjvm.grpc.handler;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.skywalking.apm.network.proto.CPU;
import org.skywalking.apm.network.proto.GC;
import org.skywalking.apm.network.proto.GCPhrase;
import org.skywalking.apm.network.proto.JVMMetric;
import org.skywalking.apm.network.proto.JVMMetrics;
import org.skywalking.apm.network.proto.JVMMetricsServiceGrpc;
import org.skywalking.apm.network.proto.Memory;
import org.skywalking.apm.network.proto.MemoryPool;
import org.skywalking.apm.network.proto.PoolType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author pengys5
 */
public class JVMMetricsServiceHandlerTestCase {

    private final Logger logger = LoggerFactory.getLogger(JVMMetricsServiceHandlerTestCase.class);

    private static JVMMetricsServiceGrpc.JVMMetricsServiceBlockingStub stub;

    public static void main(String[] args) {
        ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 11800).usePlaintext(true).build();
        stub = JVMMetricsServiceGrpc.newBlockingStub(channel);

        final long timeInterval = 1;
        Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(() -> multiInstanceJvmSend(), 1, timeInterval, TimeUnit.SECONDS);
    }

    public static void multiInstanceJvmSend() {
        buildJvmMetric(2);
        buildJvmMetric(3);
    }

    private static void buildJvmMetric(int instanceId) {
        JVMMetrics.Builder jvmMetricsBuilder = JVMMetrics.newBuilder();
        jvmMetricsBuilder.setApplicationInstanceId(instanceId);

        JVMMetric.Builder jvmMetric = JVMMetric.newBuilder();
        jvmMetric.setTime(System.currentTimeMillis());
        buildCpuMetric(jvmMetric);
        buildMemoryMetric(jvmMetric);
        buildMemoryPoolMetric(jvmMetric);
        buildGcMetric(jvmMetric);

        jvmMetricsBuilder.addMetrics(jvmMetric.build());
        stub.collect(jvmMetricsBuilder.build());
    }

    private static void buildCpuMetric(JVMMetric.Builder jvmMetric) {
        CPU.Builder cpuBuilder = CPU.newBuilder();
        cpuBuilder.setUsagePercent(70);
        jvmMetric.setCpu(cpuBuilder);
    }

    private static void buildMemoryMetric(JVMMetric.Builder jvmMetric) {
        Memory.Builder builder_1 = Memory.newBuilder();
        builder_1.setIsHeap(true);
        builder_1.setInit(20);
        builder_1.setMax(100);
        builder_1.setUsed(50);
        builder_1.setCommitted(30);
        jvmMetric.addMemory(builder_1.build());

        Memory.Builder builder_2 = Memory.newBuilder();
        builder_2.setIsHeap(false);
        builder_2.setInit(200);
        builder_2.setMax(1000);
        builder_2.setUsed(500);
        builder_2.setCommitted(300);
        jvmMetric.addMemory(builder_2.build());
    }

    private static void buildMemoryPoolMetric(JVMMetric.Builder jvmMetric) {
        MemoryPool.Builder builder_1 = MemoryPool.newBuilder();
        builder_1.setType(PoolType.NEWGEN_USAGE);
        builder_1.setIsHeap(true);
        builder_1.setInit(20);
        builder_1.setMax(100);
        builder_1.setUsed(50);
        builder_1.setCommited(30);
        jvmMetric.addMemoryPool(builder_1.build());
    }

    private static void buildGcMetric(JVMMetric.Builder jvmMetric) {
        GC.Builder newGcBuilder = GC.newBuilder();
        newGcBuilder.setPhrase(GCPhrase.NEW);
        newGcBuilder.setCount(2);
        newGcBuilder.setTime(100);
        jvmMetric.addGc(newGcBuilder.build());

        GC.Builder oldGcBuilder = GC.newBuilder();
        oldGcBuilder.setPhrase(GCPhrase.OLD);
        oldGcBuilder.setCount(2);
        oldGcBuilder.setTime(100);
        jvmMetric.addGc(oldGcBuilder.build());
    }
}
