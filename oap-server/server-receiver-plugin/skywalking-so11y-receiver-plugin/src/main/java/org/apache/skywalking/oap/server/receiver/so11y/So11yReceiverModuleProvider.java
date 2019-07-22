/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.apache.skywalking.oap.server.receiver.so11y;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.register.service.IServiceInstanceInventoryRegister;
import org.apache.skywalking.oap.server.core.register.service.IServiceInventoryRegister;
import org.apache.skywalking.oap.server.core.source.*;
import org.apache.skywalking.oap.server.library.module.*;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;
import org.apache.skywalking.oap.server.telemetry.api.Metrics;
import org.apache.skywalking.oap.server.telemetry.api.MetricsCollector;
import org.apache.skywalking.oap.server.telemetry.api.TelemetryRelatedContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.summingDouble;

/**
 * Self observability receiver provider.
 *
 * @author gaohongtao
 */
public class So11yReceiverModuleProvider extends ModuleProvider {

    private static final Logger logger = LoggerFactory.getLogger(So11yReceiverModuleProvider.class);

    private static final String SERVICE_NAME = "SkyWalking";

    private static final int RUN_RATE_SECONDS = 5;

    private final long[] lastNewGc = new long[]{0L, 0L};

    private final long[] lastOldGc = new long[]{0L, 0L};

    private int serviceId;

    private int serviceInstanceId;

    private String serviceInstanceName;

    private double lastCpuSeconds = -1;

    private IServiceInventoryRegister serviceInventoryRegister;

    private IServiceInstanceInventoryRegister serviceInstanceInventoryRegister;

    private SourceReceiver sourceReceiver;


    @Override
    public String name() {
        return "default";
    }

    @Override
    public Class<? extends ModuleDefine> module() {
        return So11yReceiverModule.class;
    }

    @Override
    public ModuleConfig createConfigBeanIfAbsent() {
        return new So11yReceiverConfig();
    }

    @Override
    public void prepare() throws ServiceNotProvidedException, ModuleStartException {
    }

    @Override
    public void start() throws ServiceNotProvidedException, ModuleStartException {
        serviceInventoryRegister = getManager().find(CoreModule.NAME).provider().getService(IServiceInventoryRegister.class);
        serviceInstanceInventoryRegister = getManager().find(CoreModule.NAME).provider().getService(IServiceInstanceInventoryRegister.class);
        sourceReceiver = getManager().find(CoreModule.NAME).provider().getService(SourceReceiver.class);
        MetricsCollector collector = getManager().find(TelemetryModule.NAME).provider().getService(MetricsCollector.class);
        Executors.newSingleThreadScheduledExecutor(new ThreadFactoryBuilder()
                .setDaemon(true).setNameFormat("So11y-receiver-%s").build()).scheduleAtFixedRate(() -> {
                    if (register()) {
                        heartbeat();
                    } else {
                        return;
                    }
                    Iterable<Metrics> mfs = collector.collect();
                    Map<String, Metrics> metricsIndex = new HashMap<>();
                    for (Metrics each : mfs) {
                        if (each.samples.size() < 1) {
                            continue;
                        }
                        metricsIndex.put(each.name, each);
                    }
                    writeCpuUsage(metricsIndex);
                    writeJvmMemory(metricsIndex);
                    writeJvmMemoryPool(metricsIndex);
                    writeGC(metricsIndex);
                }, RUN_RATE_SECONDS, RUN_RATE_SECONDS, TimeUnit.SECONDS);
    }



    @Override
    public void notifyAfterCompleted() throws ServiceNotProvidedException, ModuleStartException {

    }

    @Override
    public String[] requiredModules() {
        return new String[] {TelemetryModule.NAME, CoreModule.NAME};
    }

    private void writeGC(Map<String, Metrics> metricsIndex) {
        if (!metricsIndex.containsKey("jvm_gc_collection_seconds")) {
            return;
        }
        List<String> newGC = ImmutableList.of("PS Scavenge", "ParNew", "G1 Young Generation", "Copy");
        List<String> oldGC = ImmutableList.of("PS MarkSweep", "ConcurrentMarkSweep", "G1 Old Generation", "MarkSweepCompact");
        metricsIndex.get("jvm_gc_collection_seconds").samples.stream()
                .map(sample -> {
                    int index = Iterables.indexOf(sample.labelNames, i -> Objects.equals(i, "gc"));
                    if (index < 0) {
                        return null;
                    }
                    String gcPhrase = sample.labelValues.get(index);
                    GCMetricType type = sample.name.contains("sum") ? GCMetricType.SUM : GCMetricType.COUNT;
                    double value = type == GCMetricType.SUM ? sample.value * 1000 : sample.value;
                    if (newGC.contains(gcPhrase)) {
                        return new GCMetric(GCPhrase.NEW, type, value);
                    } else if (oldGC.contains(gcPhrase)) {
                        return new GCMetric(GCPhrase.OLD, type, value);
                    }
                    throw new RuntimeException(String.format("Unsupported gc phrase %s", gcPhrase));
                })
                .filter(Objects::nonNull)
                .collect(groupingBy(GCMetric::getPhrase))
                .forEach((gcPhrase, gcMetrics) -> {
                    ServiceInstanceJVMGC gc = new ServiceInstanceJVMGC();
                    gc.setId(serviceInstanceId);
                    gc.setName(serviceInstanceName);
                    gc.setServiceId(serviceId);
                    gc.setServiceName(SERVICE_NAME);
                    gc.setPhrase(gcPhrase);
                    long[] lastGc = gcPhrase == GCPhrase.NEW ? lastNewGc : lastOldGc;
                    gcMetrics.stream().filter(m -> m.type.equals(GCMetricType.COUNT)).findFirst().ifPresent(m -> {
                        gc.setCount(m.getValue().longValue() - lastGc[0]);
                        lastGc[0] = m.getValue().longValue();
                    });
                    gcMetrics.stream().filter(m -> m.type.equals(GCMetricType.SUM)).findFirst().ifPresent(m -> {
                        gc.setTime(m.getValue().longValue() - lastGc[1]);
                        lastGc[1] = m.getValue().longValue();
                    });
                    gc.setTimeBucket(TimeBucket.getMinuteTimeBucket(System.currentTimeMillis()));
                    if (logger.isDebugEnabled()) {
                        logger.debug("Write {} {}counts {}ms to {}", gc.getPhrase(), gc.getCount(), gc.getTime(), gc.getName());
                    }
                    sourceReceiver.receive(gc);
                });
    }

    private void writeJvmMemoryPool(Map<String, Metrics> metricsIndex) {
        List<MetricSetter<ServiceInstanceJVMMemoryPool>> setterList = ImmutableList.of(
                new MetricSetter<>("jvm_memory_pool_bytes_used", (m, v) -> m.setUsed(v.longValue())),
                new MetricSetter<>("jvm_memory_pool_bytes_committed", (m, v) -> m.setCommitted(v.longValue())),
                new MetricSetter<>("jvm_memory_pool_bytes_max", (m, v) -> m.setMax(v.longValue())),
                new MetricSetter<>("jvm_memory_pool_bytes_init", (m, v) -> m.setInit(v.longValue())));
        if (setterList.stream().anyMatch(i -> !metricsIndex.containsKey(i.name))) {
            return;
        }
        Map<MemoryPoolType, ServiceInstanceJVMMemoryPool> poolMap = new HashMap<>();
        setterList.forEach(setter -> metricsIndex.get(setter.name).samples.stream()
                .map(sample -> {
                    int index = Iterables.indexOf(sample.labelNames, i -> Objects.equals(i, "pool"));
                    if (index < 0) {
                        return null;
                    }
                    String poolType = sample.labelValues.get(index);
                    if (poolType.contains("Code")) {
                        return new PoolMetric(MemoryPoolType.CODE_CACHE_USAGE, sample.value);
                    } else if (poolType.contains("Eden")) {
                        return new PoolMetric(MemoryPoolType.NEWGEN_USAGE, sample.value);
                    } else if (poolType.contains("Survivor")) {
                        return new PoolMetric(MemoryPoolType.SURVIVOR_USAGE, sample.value);
                    } else if (poolType.contains("Old")) {
                        return new PoolMetric(MemoryPoolType.OLDGEN_USAGE, sample.value);
                    } else if (poolType.contains("Metaspace")) {
                        return new PoolMetric(MemoryPoolType.METASPACE_USAGE, sample.value);
                    } else if (poolType.contains("Perm") || poolType.contains("Compressed Class Space")) {
                        return new PoolMetric(MemoryPoolType.PERMGEN_USAGE, sample.value);
                    }
                    throw new RuntimeException(String.format("Unknown pool type %s", poolType));
                })
                .filter(Objects::nonNull)
                .collect(groupingBy(PoolMetric::getType, summingDouble(PoolMetric::getValue)))
                .forEach((memoryPoolType, value) -> {
                    if (!poolMap.containsKey(memoryPoolType)) {
                        ServiceInstanceJVMMemoryPool pool = new ServiceInstanceJVMMemoryPool();
                        pool.setId(serviceInstanceId);
                        pool.setName(serviceInstanceName);
                        pool.setServiceId(serviceId);
                        pool.setServiceName(SERVICE_NAME);
                        pool.setPoolType(memoryPoolType);
                        pool.setTimeBucket(TimeBucket.getMinuteTimeBucket(System.currentTimeMillis()));
                        poolMap.put(memoryPoolType, pool);
                    }
                    ServiceInstanceJVMMemoryPool pool = poolMap.get(memoryPoolType);
                    setter.delegated.accept(pool, value);
                }));
        poolMap.values().forEach(p -> {
            if (logger.isDebugEnabled()) {
                logger.debug("Write {} {}-{}-{}-{} to {}", p.getPoolType(),
                        humanReadableByteCount(p.getInit(), false),
                        humanReadableByteCount(p.getUsed(), false),
                        humanReadableByteCount(p.getCommitted(), false),
                        humanReadableByteCount(p.getMax(), false), p.getName());
            }
            sourceReceiver.receive(p);
        });
    }

    private void writeJvmMemory(final Map<String, Metrics> metricsIndex) {
        List<MetricSetter<ServiceInstanceJVMMemory>> setterList = ImmutableList.of(
                new MetricSetter<>("jvm_memory_bytes_used", (m, v) -> m.setUsed(v.longValue())),
                new MetricSetter<>("jvm_memory_bytes_committed", (m, v) -> m.setCommitted(v.longValue())),
                new MetricSetter<>("jvm_memory_bytes_max", (m, v) -> m.setMax(v.longValue())),
                new MetricSetter<>("jvm_memory_bytes_init", (m, v) -> m.setInit(v.longValue())));
        if (setterList.stream().anyMatch(i -> !metricsIndex.containsKey(i.name))) {
            return;
        }
        ImmutableList.of(createJVMMemory(true), createJVMMemory(false))
                .forEach(memory -> {
                    String area = memory.isHeapStatus() ? "heap" : "nonheap";
                    setterList.forEach(setter -> {
                        metricsIndex.get(setter.name).samples.stream()
                                .filter(input -> {
                                    int index = Iterables.indexOf(input.labelNames, i -> Objects.equals(i, "area"));
                                    if (index < 0) {
                                        return false;
                                    }
                                    return Objects.equals(input.labelValues.get(index), area);
                                })
                                .findFirst()
                                .ifPresent(sample -> setter.delegated.accept(memory, sample.value));
                    });
                    if (logger.isDebugEnabled()) {
                        logger.debug("Write {} {}-{}-{}-{} to {}", area,
                                humanReadableByteCount(memory.getInit(), false),
                                humanReadableByteCount(memory.getUsed(), false),
                                humanReadableByteCount(memory.getCommitted(), false),
                                humanReadableByteCount(memory.getMax(), false), memory.getName());
                    }
                    sourceReceiver.receive(memory);
                });
    }

    private ServiceInstanceJVMMemory createJVMMemory(boolean isHeap) {
        ServiceInstanceJVMMemory memory = new ServiceInstanceJVMMemory();
        memory.setId(serviceInstanceId);
        memory.setName(serviceInstanceName);
        memory.setServiceId(serviceId);
        memory.setServiceName(SERVICE_NAME);
        memory.setHeapStatus(isHeap);
        memory.setTimeBucket(TimeBucket.getMinuteTimeBucket(System.currentTimeMillis()));
        return memory;
    }

    private void writeCpuUsage(Map<String, Metrics> metricsIndex) {
        if (!metricsIndex.containsKey("process_cpu_seconds_total")) {
            return;
        }
        double value = metricsIndex.get("process_cpu_seconds_total").samples.get(0).value;
        if (lastCpuSeconds < 0) {
            lastCpuSeconds = value;
            return;
        }
        double percentage = (value - lastCpuSeconds) * 100 / (RUN_RATE_SECONDS * Runtime.getRuntime().availableProcessors());
        lastCpuSeconds = value;
        ServiceInstanceJVMCPU serviceInstanceJVMCPU = new ServiceInstanceJVMCPU();
        serviceInstanceJVMCPU.setId(serviceInstanceId);
        serviceInstanceJVMCPU.setName(serviceInstanceName);
        serviceInstanceJVMCPU.setServiceId(serviceId);
        serviceInstanceJVMCPU.setServiceName(SERVICE_NAME);
        serviceInstanceJVMCPU.setUsePercent(percentage);
        serviceInstanceJVMCPU.setTimeBucket(TimeBucket.getMinuteTimeBucket(System.currentTimeMillis()));
        logger.debug("Write so11y cpu usage {} to {}", percentage, serviceInstanceName);
        sourceReceiver.receive(serviceInstanceJVMCPU);
    }


    private void heartbeat() {
        long now = System.currentTimeMillis();
        serviceInventoryRegister.heartbeat(serviceId, now);
        serviceInstanceInventoryRegister.heartbeat(serviceInstanceId, now);
    }

    private boolean register() {
        if (serviceId == Const.NONE) {
            logger.debug("Register so11y service [{}].", SERVICE_NAME);
            serviceId = serviceInventoryRegister.getOrCreate(SERVICE_NAME, null);
        }
        if (serviceId != Const.NONE && serviceInstanceId == Const.NONE) {
            serviceInstanceName = TelemetryRelatedContext.INSTANCE.getId();
            logger.debug("Register so11y service instance [{}].", serviceInstanceName);
            serviceInstanceId = serviceInstanceInventoryRegister.getOrCreate(serviceId, serviceInstanceName, serviceInstanceName,
                    System.currentTimeMillis(), null);
        }
        return serviceInstanceId != Const.NONE;
    }

    @RequiredArgsConstructor
    private class MetricSetter<T> {

        final String name;

        final BiConsumer<T, Double> delegated;

    }

    private static String humanReadableByteCount(long bytes, boolean si) {
        int unit = si ? 1000 : 1024;
        if (bytes < unit) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(unit));
        String pre = (si ? "kMGTPE" : "KMGTPE").charAt(exp - 1) + (si ? "" : "i");
        return String.format("%.1f %sB", bytes / Math.pow(unit, exp), pre);
    }

    @RequiredArgsConstructor
    @EqualsAndHashCode(of = "type")
    @ToString
    @Getter
    private class PoolMetric {
        private final MemoryPoolType type;
        private final Double value;
    }

    @RequiredArgsConstructor
    @EqualsAndHashCode(of = "phrase")
    @ToString
    @Getter
    private class GCMetric {
        private final GCPhrase phrase;
        private final GCMetricType type;
        private final Double value;
    }

    private enum GCMetricType {
        SUM, COUNT
    }

}
