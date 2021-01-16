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

package org.apache.skywalking.oap.server.receiver.envoy;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.Lists;
import io.prometheus.client.Collector;
import io.prometheus.client.CounterMetricFamily;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * ALSCounterCollector intend to expose metrics with limit size.
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@Slf4j
public class ALSCounterCollector extends Collector {

    private final String name;
    private final String help;
    private final String[] labelNames;
    private final Cache<String, CounterSample> cache;

    static ALSCounterCollector newInstance(final String name, final String help, final String... labelNames) {
        return new ALSCounterCollector(name, help, labelNames, CacheBuilder.newBuilder()
            .maximumSize(1000)
            .build());
    }

    void inc(final String key, final int number, final String... labels) {
        try {
            cache.get(key, () -> new CounterSample(key, labels)).value.addAndGet(number);
        } catch (final ExecutionException e) {
            log.error("Get metric cache error", e);
        }
    }

    @Override public List<MetricFamilySamples> collect() {
        List<MetricFamilySamples> mfs = new ArrayList<>();
        CounterMetricFamily labeledCounter = new CounterMetricFamily(name, help, Arrays.asList(labelNames));
        cache.asMap().values().forEach(c -> {
            if (labelNames.length != (1 + c.labels.length)) {
                log.warn("Labels count is incorrect {}, {}", labelNames, c.labels);
                return;
            }
            labeledCounter.addMetric(Lists.asList(c.key, c.labels), c.value.doubleValue());
        });
        mfs.add(labeledCounter);
        return mfs;
    }

    @RequiredArgsConstructor
    private static class CounterSample {
        private final String key;

        private final String[] labels;

        private final AtomicInteger value = new AtomicInteger();
    }
}
