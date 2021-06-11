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

package org.apache.skywalking.oap.server.analyzer.provider.jvm;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.Collections;

import com.google.common.collect.ImmutableMap;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.network.language.agent.v3.GC;
import org.apache.skywalking.apm.network.language.agent.v3.JVMMetric;
import org.apache.skywalking.apm.network.language.agent.v3.Memory;
import org.apache.skywalking.apm.network.language.agent.v3.MemoryPool;
import org.apache.skywalking.apm.network.language.agent.v3.Thread;
import org.apache.skywalking.oap.meter.analyzer.MetricConvert;
import org.apache.skywalking.oap.meter.analyzer.dsl.Sample;
import org.apache.skywalking.oap.meter.analyzer.dsl.SampleFamily;
import org.apache.skywalking.oap.meter.analyzer.dsl.SampleFamilyBuilder;
import org.apache.skywalking.oap.meter.analyzer.prometheus.rule.Rule;
import org.apache.skywalking.oap.server.analyzer.provider.meter.process.SampleBuilder;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterSystem;
import org.apache.skywalking.oap.server.library.module.ModuleManager;

@Slf4j
public class JVMSourceDispatcher {

    private final List<MetricConvert> metricConverts;

    public JVMSourceDispatcher(ModuleManager moduleManager, List<Rule> rules) {
        this.metricConverts = rules.stream()
                .map(it -> new MetricConvert(it, moduleManager.find(CoreModule.NAME).provider().getService(MeterSystem.class)))
                .collect(Collectors.toList());
    }

    public void sendMetric(String service, String serviceInstance, JVMMetric jvmMetric) {
        List<Sample> cpuSamples = Collections.singletonList(parseCpuData(service, serviceInstance, jvmMetric));
        List<Sample> memorySamples = parseMemoryData(service, serviceInstance, jvmMetric);
        List<Sample> memoryPoolSamples = parseMemoryPollData(service, serviceInstance, jvmMetric);
        List<Sample> gcCountSamples = parseGcCountData(service, serviceInstance, jvmMetric);
        List<Sample> gcTimeSamples = parseGcTimeData(service, serviceInstance, jvmMetric);
        List<Sample> threadSamples = parseThreadData(service, serviceInstance, jvmMetric);

        ImmutableMap<String, SampleFamily> sampleFamilies = ImmutableMap.<String, SampleFamily>builder()
                .put("sw_jvm_gc_time", SampleFamilyBuilder.newBuilder(gcTimeSamples.toArray(new Sample[0])).build())
                .put("sw_jvm_gc_count", SampleFamilyBuilder.newBuilder(gcCountSamples.toArray(new Sample[0])).build())
                .put("sw_jvm_cpu", SampleFamilyBuilder.newBuilder(cpuSamples.toArray(new Sample[0])).build())
                .put("sw_jvm_thread", SampleFamilyBuilder.newBuilder(threadSamples.toArray(new Sample[0])).build())
                .put("sw_jvm_memory", SampleFamilyBuilder.newBuilder(memorySamples.toArray(new Sample[0])).build())
                .put("sw_jvm_memory_poll", SampleFamilyBuilder.newBuilder(memoryPoolSamples.toArray(new Sample[0])).build())
                .build();

        metricConverts.forEach(metricConvert -> metricConvert.toMeter(sampleFamilies));
    }

    private List<Sample> parseThreadData(String service, String serviceInstance, JVMMetric jvmMetric) {
        Thread thread = jvmMetric.getThread();
        return Arrays.asList(
                buildThreadSample(thread.getDaemonCount(), "daemon", service, serviceInstance, jvmMetric.getTime()),
                buildThreadSample(thread.getLiveCount(), "live", service, serviceInstance, jvmMetric.getTime()),
                buildThreadSample(thread.getPeakCount(), "peak", service, serviceInstance, jvmMetric.getTime())
        );
    }

    private List<Sample> parseGcCountData(String service, String serviceInstance, JVMMetric jvmMetric) {
        return jvmMetric.getGcList().stream().map(gc ->
                buildGcSample(gc, gc.getCount(), "sw_jvm_gc_count", service, serviceInstance, jvmMetric.getTime())
        ).collect(Collectors.toList());
    }

    private List<Sample> parseGcTimeData(String service, String serviceInstance, JVMMetric jvmMetric) {
        return jvmMetric.getGcList().stream().map(gc ->
                buildGcSample(gc, gc.getTime(), "sw_jvm_gc_time", service, serviceInstance, jvmMetric.getTime())
        ).collect(Collectors.toList());
    }

    private Sample parseCpuData(String service, String serviceInstance, JVMMetric jvmMetric) {
        SampleBuilder.SampleBuilderBuilder sampleBuilderBuilder = SampleBuilder.builder();
        double adjustedCpuUsagePercent = Math.max(jvmMetric.getCpu().getUsagePercent(), 1.0);
        sampleBuilderBuilder.name("sw_jvm_cpu");
        sampleBuilderBuilder.value(adjustedCpuUsagePercent);
        sampleBuilderBuilder.labels(ImmutableMap.<String, String>builder().build());
        return sampleBuilderBuilder.build().build(service, serviceInstance, jvmMetric.getTime());
    }

    private List<Sample> parseMemoryData(String service, String serviceInstance, JVMMetric jvmMetric) {
        return jvmMetric.getMemoryList().stream().map(memory -> Arrays.asList(
                buildMemorySample(memory, memory.getInit(), "init", service, serviceInstance, jvmMetric.getTime()),
                buildMemorySample(memory, memory.getMax(), "max", service, serviceInstance, jvmMetric.getTime()),
                buildMemorySample(memory, memory.getCommitted(), "committed", service, serviceInstance, jvmMetric.getTime()),
                buildMemorySample(memory, memory.getUsed(), "used", service, serviceInstance, jvmMetric.getTime())
        )).flatMap(Collection::stream).collect(Collectors.toList());
    }

    private List<Sample> parseMemoryPollData(String service, String serviceInstance, JVMMetric jvmMetric) {
        return jvmMetric.getMemoryPoolList().stream().map(memoryPool -> Arrays.asList(
                buildMemoryPoolSample(memoryPool, memoryPool.getInit(), "init", service, serviceInstance, jvmMetric.getTime()),
                buildMemoryPoolSample(memoryPool, memoryPool.getMax(), "max", service, serviceInstance, jvmMetric.getTime()),
                buildMemoryPoolSample(memoryPool, memoryPool.getCommitted(), "committed", service, serviceInstance, jvmMetric.getTime()),
                buildMemoryPoolSample(memoryPool, memoryPool.getUsed(), "used", service, serviceInstance, jvmMetric.getTime())
        )).flatMap(Collection::stream).collect(Collectors.toList());
    }

    private Sample buildGcSample(GC gc, long value, String name, String service, String serviceInstance, long time) {
        SampleBuilder.SampleBuilderBuilder sampleBuilderBuilder = SampleBuilder.builder();
        sampleBuilderBuilder.name(name);
        sampleBuilderBuilder.value(value);
        switch (gc.getPhrase()) {
            case NEW:
                sampleBuilderBuilder.labels(ImmutableMap.of("gc_phrase", "new"));
                break;
            case OLD:
                sampleBuilderBuilder.labels(ImmutableMap.of("gc_phrase", "old"));
                break;
            default:
        }
        return sampleBuilderBuilder.build().build(service, serviceInstance, time);
    }

    private Sample buildThreadSample(long value, String threadType, String service, String serviceInstance, long time) {
        SampleBuilder.SampleBuilderBuilder sampleBuilderBuilder = SampleBuilder.builder();
        sampleBuilderBuilder.name("sw_jvm_thread");
        sampleBuilderBuilder.value(value);
        sampleBuilderBuilder.labels(ImmutableMap.of("thread_type", threadType));
        return sampleBuilderBuilder.build().build(service, serviceInstance, time);
    }

    private Sample buildMemorySample(Memory memory, long value, String memoryType, String service, String serviceInstance, long time) {
        SampleBuilder.SampleBuilderBuilder sampleBuilderBuilder = SampleBuilder.builder();
        sampleBuilderBuilder.name("sw_jvm_memory");
        sampleBuilderBuilder.labels(ImmutableMap.of("heap_status", String.valueOf(memory.getIsHeap()), "memory_type", memoryType));
        sampleBuilderBuilder.value(value);
        return sampleBuilderBuilder.build().build(service, serviceInstance, time);
    }

    private Sample buildMemoryPoolSample(MemoryPool memoryPool, long value, String memoryType, String service, String serviceInstance, long time) {
        SampleBuilder.SampleBuilderBuilder sampleBuilderBuilder = SampleBuilder.builder();
        sampleBuilderBuilder.name("sw_jvm_memory_poll");
        sampleBuilderBuilder.value(value);
        String pollType = "poll_type";
        String memoryTypeKey = "memory_type";
        switch (memoryPool.getType()) {
            case NEWGEN_USAGE:
                sampleBuilderBuilder.labels(ImmutableMap.of(pollType, "memoryTypeKey", memoryTypeKey, memoryType));
                break;
            case OLDGEN_USAGE:
                sampleBuilderBuilder.labels(ImmutableMap.of(pollType, "oldgenUsage", memoryTypeKey, memoryType));
                break;
            case PERMGEN_USAGE:
                sampleBuilderBuilder.labels(ImmutableMap.of(pollType, "permgenUsage", memoryTypeKey, memoryType));
                break;
            case SURVIVOR_USAGE:
                sampleBuilderBuilder.labels(ImmutableMap.of(pollType, "survivorUsage", memoryTypeKey, memoryType));
                break;
            case METASPACE_USAGE:
                sampleBuilderBuilder.labels(ImmutableMap.of(pollType, "metaspaceUsage", memoryTypeKey, memoryType));
                break;
            case CODE_CACHE_USAGE:
                sampleBuilderBuilder.labels(ImmutableMap.of(pollType, "codeCacheUsage", memoryTypeKey, memoryType));
                break;
            default:
        }
        return sampleBuilderBuilder.build().build(service, serviceInstance, time);
    }
}
