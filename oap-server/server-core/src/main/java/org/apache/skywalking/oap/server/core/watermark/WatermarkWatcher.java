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
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import lombok.RequiredArgsConstructor;
import org.apache.skywalking.oap.server.telemetry.api.MetricsCollector;

@RequiredArgsConstructor
public class WatermarkWatcher {
    private final long maxHeapMemoryUsagePercentThreshold;
    private final long maxNoHeapMemoryUsagePercentThreshold;
    private final long maxNoHeapMemoryUsageThreshold;

    private MetricsCollector so11yCollector;
    /**
     * Noheap memory max, jvm_memory_max_bytes{area="nonheap"}
     */
    private long noheapMemoryMax = 0;
    /**
     * Noheap memory used, jvm_memory_used_bytes{area="nonheap"}
     */
    private long noheapMemoryUsed = 0;
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

    public void start(MetricsCollector so11yCollector) {
        this.so11yCollector = so11yCollector;
        Executors.newSingleThreadScheduledExecutor()
                 .scheduleWithFixedDelay(this::watch, 0, 10, TimeUnit.SECONDS);
        lock = new ReentrantLock();
        listeners = new ArrayList<>();
    }

    private void watch() {
        so11yCollector.find("jvm_memory_bytes_max")
                      .ifPresent(metricFamily -> {
                          metricFamily.samples.forEach(sample -> {
                              for (int i = 0; i < sample.labelNames.size(); i++) {
                                  if (sample.labelNames.get(i).equals("area") && sample.labelValues.get(i)
                                                                                                   .equals("heap")) {
                                      heapMemoryMax += (long) sample.value;
                                  } else if (sample.labelNames.get(i).equals("area") && sample.labelValues.get(i)
                                                                                                          .equals(
                                                                                                              "nonheap")) {
                                      noheapMemoryMax = (long) sample.value;
                                  }
                              }
                          });
                      });
        so11yCollector.find("jvm_memory_bytes_used")
                      .ifPresent(metricFamily -> {
                          metricFamily.samples.forEach(sample -> {
                              for (int i = 0; i < sample.labelNames.size(); i++) {
                                  if (sample.labelNames.get(i).equals("area") && sample.labelValues.get(i)
                                                                                                   .equals("heap")) {
                                      heapMemoryUsed += (long) sample.value;
                                  } else if (sample.labelNames.get(i).equals("area") && sample.labelValues.get(i)
                                                                                                          .equals(
                                                                                                              "nonheap")) {
                                      noheapMemoryUsed = (long) sample.value;
                                  }
                              }
                          });
                      });
        if (heapMemoryMax <= 0) {
            // No heap memory metrics found, skip the watermark control.
            return;
        }
        lock.lock();
        try {
            long heapMemoryUsagePercent = heapMemoryUsed * 100 / heapMemoryMax;
            if (heapMemoryUsagePercent > maxHeapMemoryUsagePercentThreshold) {
                listeners.forEach(
                    watermarkListener -> watermarkListener.notify(WatermarkEvent.Type.HEAP_MEMORY_USAGE_PERCENTAGE));
            }
            if (noheapMemoryMax > 0) {
                long noheapMemoryUsagePercent = noheapMemoryUsed * 100 / noheapMemoryMax;
                if (noheapMemoryUsagePercent > maxNoHeapMemoryUsagePercentThreshold) {
                    listeners.forEach(
                        watermarkListener -> watermarkListener.notify(
                            WatermarkEvent.Type.NO_HEAP_MEMORY_USAGE_PERCENTAGE));
                }
            }
            if (maxNoHeapMemoryUsageThreshold > 0) {
                if (noheapMemoryUsed > maxNoHeapMemoryUsageThreshold) {
                    listeners.forEach(
                        watermarkListener -> watermarkListener.notify(WatermarkEvent.Type.NO_HEAP_MEMORY_USAGE));
                }
            }
        } finally {
            lock.unlock();
        }
    }

    public void addListener(WatermarkListener listener) {
        lock.lock();
        try {
            listeners.add(listener);
        } finally {
            lock.unlock();
        }
    }
}
