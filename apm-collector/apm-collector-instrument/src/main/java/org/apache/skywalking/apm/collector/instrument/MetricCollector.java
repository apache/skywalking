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

package org.apache.skywalking.apm.collector.instrument;

import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The <code>MetricCollector</code> collects the service metrics by Module/Provider/Service structure.
 */
public enum MetricCollector implements Runnable {
    INSTANCE;

    private final Logger logger = LoggerFactory.getLogger(MetricCollector.class);
    private ConcurrentHashMap<String, ServiceMetric> serviceMetricsA = new ConcurrentHashMap<>();
    private ConcurrentHashMap<String, ServiceMetric> serviceMetricsB = new ConcurrentHashMap<>();
    private volatile boolean isA = true;

    MetricCollector() {
        ScheduledExecutorService service = Executors
            .newSingleThreadScheduledExecutor();
        service.scheduleAtFixedRate(this, 10, 60, TimeUnit.SECONDS);
    }

    @Override
    public void run() {
        ConcurrentHashMap<String, ServiceMetric> now;
        if (isA) {
            now = serviceMetricsA;
            isA = false;
        } else {
            now = serviceMetricsB;
            isA = true;
        }
        try {
            // Wait the A/B switch completed.
            Thread.sleep(1000L);
        } catch (InterruptedException e) {

        }

        StringBuilder report = new StringBuilder();
        report.append("\n");
        report.append("##################################################################################################################\n");
        report.append("#                                             Collector Service Report                                           #\n");
        report.append("##################################################################################################################\n");
        now.forEach((serviceName, serviceMetric) -> {
            report.append(serviceName).append(":\n");
            serviceMetric.methodMetrics.forEach((method, metric) -> {
                if (metric.isExecuted()) {
                    report.append(method).append(":\n");
                    report.append("\t").append(metric).append("\n");
                    metric.clear();
                }
            });
            serviceMetric.reset();
        });

        /**
         * The reason of outputing in warn info, is to make sure this logs aren't blocked in performance test.(debug/info log level off)
         */
        logger.warn(report.toString());

    }

    void registerService(String service) {
        serviceMetricsA.put(service, new ServiceMetric(service));
        serviceMetricsB.put(service, new ServiceMetric(service));
    }

    void trace(String service, Method method, long nano, boolean occurException) {
        ConcurrentHashMap<String, ServiceMetric> now = isA ? serviceMetricsA : serviceMetricsB;
        now.get(service).trace(method, nano, occurException);
    }

    class ServiceMetric {
        private String serviceName;
        private ConcurrentHashMap<Method, ServiceMethodMetric> methodMetrics = new ConcurrentHashMap<>();
        private volatile boolean isExecuted = false;

        public ServiceMetric(String serviceName) {
            this.serviceName = serviceName;
        }

        private void reset() {
            isExecuted = false;
        }

        void trace(Method method, long nano, boolean occurException) {
            isExecuted = true;
            ServiceMethodMetric metric = methodMetrics.get(method);
            if (metric == null) {
                ServiceMethodMetric methodMetric = new ServiceMethodMetric();
                methodMetrics.putIfAbsent(method, methodMetric);
                metric = methodMetrics.get(method);
            }
            metric.add(nano, occurException);
        }
    }

    private class ServiceMethodMetric {
        private AtomicLong totalTimeNano;
        private AtomicLong counter;
        private AtomicLong errorCounter;

        public ServiceMethodMetric() {
            totalTimeNano = new AtomicLong(0);
            counter = new AtomicLong(0);
            errorCounter = new AtomicLong(0);
        }

        private boolean isExecuted() {
            return counter.get() > 0;
        }

        private void add(long nano, boolean occurException) {
            totalTimeNano.addAndGet(nano);
            counter.incrementAndGet();
            if (occurException)
                errorCounter.incrementAndGet();
        }

        private void clear() {
            totalTimeNano.set(0);
            counter.set(0);
            errorCounter.set(0);
        }

        @Override public String toString() {
            if (counter.longValue() == 0) {
                return "Avg=N/A";
            }
            return "Avg=" + (totalTimeNano.longValue() / counter.longValue()) + " (nano)" +
                ", Success Rate=" + (counter.longValue() - errorCounter.longValue()) * 100 / counter.longValue() +
                "%, Calls=" + counter.longValue();
        }
    }
}
