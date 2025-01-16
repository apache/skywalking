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

package org.apache.skywalking.oap.server.core.watermark;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.TerminalFriendlyTable;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;
import org.apache.skywalking.oap.server.telemetry.api.CounterMetrics;
import org.apache.skywalking.oap.server.telemetry.api.MetricsCollector;
import org.apache.skywalking.oap.server.telemetry.api.MetricsCreator;
import org.apache.skywalking.oap.server.telemetry.api.MetricsTag;

/**
 * WatermarkWatcher is a component to watch the key metrics of the system, and trigger the watermark event when the
 * system is overloaded.
 */
@RequiredArgsConstructor
@Slf4j
public class WatermarkWatcher {
    private final ModuleManager moduleManager;
    private final long maxHeapMemoryUsagePercentThreshold;
    private final long maxDirectHeapMemoryUsageThreshold;

    private MetricsCollector so11yCollector;
    /**
     * Noheap memory used, jvm_memory_used_bytes{area="nonheap"}
     */
    private long directMemoryUsed = 0;
    /**
     * Heap memory max, jvm_memory_max_bytes{area="heap"}. Use the combination of all available ids of heap memory.
     */
    private long heapMemoryMax = 0;
    /**
     * Heap memory used, jvm_memory_used_bytes{area="heap"}, Use the combination of all available ids of heap memory.
     */
    private long heapMemoryUsed = 0;
    private ReentrantLock lock;
    private List<WatermarkListener> listeners;
    private volatile boolean isLimiting = false;
    private Map<WatermarkEvent.Type, Map<String, CounterMetrics>> breakCounters;
    private Map<String, CounterMetrics> recoverCounters;
    private MetricsCreator metricsCreator;

    public void start(MetricsCollector so11yCollector) {
        this.so11yCollector = so11yCollector;
        lock = new ReentrantLock();
        listeners = new ArrayList<>();
        breakCounters = new HashMap<>();
        recoverCounters = new HashMap<>();
        for (WatermarkEvent.Type type : WatermarkEvent.Type.values()) {
            breakCounters.put(type, new HashMap<>());
        }
        metricsCreator = moduleManager.find(TelemetryModule.NAME)
                                                          .provider()
                                                          .getService(MetricsCreator.class);
        this.addListener(WatermarkGRPCInterceptor.INSTANCE);

        Executors.newSingleThreadScheduledExecutor()
                 .scheduleWithFixedDelay(this::watch, 0, 10, TimeUnit.SECONDS);
    }

    private void watch() {
        this.heapMemoryUsed = so11yCollector.heapMemoryUsage();
        this.heapMemoryMax = so11yCollector.heapMemoryMax();
        this.directMemoryUsed = so11yCollector.directMemoryUsage();

        if (log.isDebugEnabled()) {
            TerminalFriendlyTable table = new TerminalFriendlyTable("Watermark Controller Key Metrics");
            table.addRow(new TerminalFriendlyTable.Row("Heap Memory Max", String.format("%,d", heapMemoryMax)));
            table.addRow(new TerminalFriendlyTable.Row("Heap Memory Used", String.format("%,d", heapMemoryUsed)));
            table.addRow(new TerminalFriendlyTable.Row("Heap Memory Usage Percentage", heapMemoryUsagePercent() + "%"));
            table.addRow(new TerminalFriendlyTable.Row("Direct Memory Used", String.format("%,d", directMemoryUsed)));
            log.debug(table.toString());
        }

        boolean isLimitingTriggered = false;

        if (heapMemoryUsagePercent() > maxHeapMemoryUsagePercentThreshold) {
            this.notify(WatermarkEvent.Type.HEAP_MEMORY_USAGE_PERCENTAGE);
            isLimitingTriggered = true;
        }

        if (maxDirectHeapMemoryUsageThreshold > 0 && directMemoryUsed > 0) {
            if (directMemoryUsed > maxDirectHeapMemoryUsageThreshold) {
                this.notify(WatermarkEvent.Type.DIRECT_HEAP_MEMORY_USAGE);
                isLimitingTriggered = true;
            }
        }

        if (!isLimitingTriggered && isLimiting) {
            recovered();
        }
    }

    private void notify(WatermarkEvent.Type event) {
        if (isLimiting) {
            return;
        }
        TerminalFriendlyTable table = new TerminalFriendlyTable("Watermark Controller Key Metrics");
        table.addRow(new TerminalFriendlyTable.Row("Heap Memory Max", String.format("%,d", heapMemoryMax)));
        table.addRow(new TerminalFriendlyTable.Row("Heap Memory Used", String.format("%,d", heapMemoryUsed)));
        table.addRow(new TerminalFriendlyTable.Row("Heap Memory Usage Percentage", heapMemoryUsagePercent() + "%"));
        table.addRow(new TerminalFriendlyTable.Row("Direct Memory Used", String.format("%,d", directMemoryUsed)));
        table.addRow(new TerminalFriendlyTable.Row("Event", event.name()));

        isLimiting = true;

        lock.lock();
        try {
            listeners.forEach(listener -> {
                listener.notify(event);
                table.addRow(new TerminalFriendlyTable.Row("Notified Listener", listener.getName()));
                breakCounters.get(event).get(listener.getName()).inc();
            });
        } finally {
            lock.unlock();
        }
        log.warn(table.toString());
    }

    private void recovered() {
        TerminalFriendlyTable table = new TerminalFriendlyTable("Watermark Controller Key Metrics");
        table.addRow(new TerminalFriendlyTable.Row("Heap Memory Max", String.format("%,d", heapMemoryMax)));
        table.addRow(new TerminalFriendlyTable.Row("Heap Memory Used", String.format("%,d", heapMemoryUsed)));
        table.addRow(new TerminalFriendlyTable.Row("Heap Memory Usage Percentage", heapMemoryUsagePercent() + "%"));
        table.addRow(new TerminalFriendlyTable.Row("Direct Memory Used", String.format("%,d", directMemoryUsed)));
        table.addRow(new TerminalFriendlyTable.Row("Event", "RECOVERED"));

        isLimiting = false;
        lock.lock();
        try {
            listeners.forEach(listener -> {
                listener.beAwareOfRecovery();
                table.addRow(new TerminalFriendlyTable.Row("Notified Listener", listener.getName()));
                recoverCounters.get(listener.getName()).inc();
            });
        } finally {
            lock.unlock();
        }
        log.info(table.toString());
    }

    private long heapMemoryUsagePercent() {
        if (heapMemoryMax > 0) {
            return heapMemoryUsed * 100 / heapMemoryMax;
        } else {
            return -1;
        }
    }

    private MetricsCreator getMetricsCreator() {
        if (metricsCreator == null) {
            metricsCreator = moduleManager.find(TelemetryModule.NAME)
                                                          .provider()
                                                          .getService(MetricsCreator.class);
        }
        return metricsCreator;
    }

    public void addListener(WatermarkListener listener) {
        lock.lock();
        try {
            listeners.add(listener);
            MetricsCreator metricsCreator = getMetricsCreator();
            for (WatermarkEvent.Type type : WatermarkEvent.Type.values()) {
                breakCounters.get(type).put(listener.getName(), metricsCreator.createCounter(
                    "watermark_circuit_breaker_break_count", "The number of times the watermark circuit breaker breaks",
                    new MetricsTag.Keys("listener", "event"),
                    new MetricsTag.Values(listener.getName(), type.name())
                ));
            }
            recoverCounters.put(listener.getName(), metricsCreator.createCounter(
                "watermark_circuit_breaker_recover_count", "The number of times the watermark circuit breaker recovers",
                new MetricsTag.Keys("listener"),
                new MetricsTag.Values(listener.getName())
            ));
        } finally {
            lock.unlock();
        }
    }
}
