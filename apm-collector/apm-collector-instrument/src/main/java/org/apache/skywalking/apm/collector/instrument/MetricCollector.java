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
import java.util.HashMap;
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
    private HashMap<String, ModuleMetric> modules = new HashMap<>();

    MetricCollector() {
        ScheduledExecutorService service = Executors
            .newSingleThreadScheduledExecutor();
        service.scheduleAtFixedRate(this, 10, 60, TimeUnit.SECONDS);
    }

    @Override
    public void run() {
        if (!logger.isDebugEnabled()) {
            return;
        }
        StringBuilder report = new StringBuilder();
        report.append("\n");
        report.append("##################################################################################################################\n");
        report.append("#                                             Collector Service Report                                           #\n");
        report.append("##################################################################################################################\n");
        modules.forEach((moduleName, moduleMetric) -> {
            report.append(moduleName).append(":\n");
            moduleMetric.providers.forEach((providerName, providerMetric) -> {
                report.append("\t").append(providerName).append(":\n");
                providerMetric.services.forEach((serviceName, serviceMetric) -> {
                    serviceMetric.methodMetrics.forEach((method, metric) -> {
                        report.append("\t\t").append(method).append(":\n");
                        report.append("\t\t\t").append(metric).append("\n");
                        serviceMetric.methodMetrics.put(method, new ServiceMethodMetric());
                    });
                });
            });
        });

        logger.debug(report.toString());

    }

    ServiceMetric registerService(String module, String provider, String service) {
        return initIfAbsent(module).initIfAbsent(provider).initIfAbsent(service);
    }

    private ModuleMetric initIfAbsent(String moduleName) {
        if (!modules.containsKey(moduleName)) {
            ModuleMetric metric = new ModuleMetric(moduleName);
            modules.put(moduleName, metric);
            return metric;
        }
        return modules.get(moduleName);
    }

    private class ModuleMetric {
        private String moduleName;
        private HashMap<String, ProviderMetric> providers = new HashMap<>();

        public ModuleMetric(String moduleName) {
            this.moduleName = moduleName;
        }

        private ProviderMetric initIfAbsent(String providerName) {
            if (!providers.containsKey(providerName)) {
                ProviderMetric metric = new ProviderMetric(providerName);
                providers.put(providerName, metric);
                return metric;
            }
            return providers.get(providerName);
        }
    }

    private class ProviderMetric {
        private String providerName;
        private HashMap<String, ServiceMetric> services = new HashMap<>();

        public ProviderMetric(String providerName) {
            this.providerName = providerName;
        }

        private ServiceMetric initIfAbsent(String serviceName) {
            if (!services.containsKey(serviceName)) {
                ServiceMetric metric = new ServiceMetric(serviceName);
                services.put(serviceName, metric);
                return metric;
            }
            return services.get(serviceName);
        }
    }

    class ServiceMetric {
        private String serviceName;
        private ConcurrentHashMap<Method, ServiceMethodMetric> methodMetrics = new ConcurrentHashMap<>();

        public ServiceMetric(String serviceName) {
            this.serviceName = serviceName;
        }

        void trace(Method method, long nano, boolean occurException) {
            if (logger.isDebugEnabled()) {
                ServiceMethodMetric metric = methodMetrics.get(method);
                if (metric == null) {
                    ServiceMethodMetric methodMetric = new ServiceMethodMetric();
                    methodMetrics.putIfAbsent(method, methodMetric);
                    metric = methodMetrics.get(method);
                }
                metric.add(nano, occurException);
            }
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

        private void add(long nano, boolean occurException) {
            totalTimeNano.addAndGet(nano);
            counter.incrementAndGet();
            if (occurException)
                errorCounter.incrementAndGet();
        }

        @Override public String toString() {
            if (counter.longValue() == 0) {
                return "Avg=N/A";
            }
            return "Avg=" + (totalTimeNano.longValue() / counter.longValue()) + " (nano)" +
                ", Success Rate=" + (counter.longValue() - errorCounter.longValue()) * 100 / counter.longValue() +
                "%";
        }
    }
}
