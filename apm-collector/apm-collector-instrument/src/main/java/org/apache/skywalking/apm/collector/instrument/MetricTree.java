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

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.*;
import org.apache.skywalking.apm.collector.core.annotations.trace.BatchParameter;
import org.slf4j.*;

/**
 * @author wusheng, peng-yongsheng
 */
public enum MetricTree implements Runnable {
    INSTANCE;

    private static final Logger logger = LoggerFactory.getLogger(MetricTree.class);

    private List<MetricNode> metrics = new LinkedList<>();
    private String lineSeparator = System.getProperty("line.separator");

    MetricTree() {
        ScheduledExecutorService service = Executors.newSingleThreadScheduledExecutor();
        service.scheduleAtFixedRate(this, 60, 60, TimeUnit.SECONDS);
    }

    synchronized MetricNode lookup(String metricName) {
        MetricNode node = new MetricNode(metricName);
        metrics.add(node);
        return node;
    }

    @Override
    public void run() {
        try {
            metrics.forEach(MetricNode::exchange);

            StringBuilder logBuffer = new StringBuilder();
            logBuffer.append(lineSeparator);
            logBuffer.append("##################################################################################################################").append(lineSeparator);
            logBuffer.append("#                                             Collector Service Report                                           #").append(lineSeparator);
            logBuffer.append("##################################################################################################################").append(lineSeparator);

            metrics.forEach((MetricNode metric) -> metric.toOutput(new ReportWriter() {
                @Override public void writeMetricName(String name) {
                    logBuffer.append(name).append(lineSeparator);
                }

                @Override public void writeMetric(String metrics) {
                    logBuffer.append("\t");
                    logBuffer.append(metrics).append(lineSeparator);
                }
            }));

            metrics.forEach(MetricNode::clear);

            logger.warn(logBuffer.toString());
        } catch (Throwable e) {
            logger.error(e.getMessage(), e);
        }
    }

    class MetricNode {
        private final String metricName;
        private volatile ServiceMetric metric;

        MetricNode(String metricName) {
            this.metricName = metricName;
        }

        ServiceMetric getMetric(Method targetMethod) {
            if (metric == null) {
                int detectedBatchIndex = -1;
                if (targetMethod != null) {
                    Annotation[][] annotations = targetMethod.getParameterAnnotations();
                    if (annotations != null) {
                        int index = 0;
                        for (Annotation[] parameterAnnotation : annotations) {
                            if (parameterAnnotation != null) {
                                for (Annotation annotation : parameterAnnotation) {
                                    if (annotation instanceof BatchParameter) {
                                        detectedBatchIndex = index;
                                        break;
                                    }
                                }
                            }
                            if (detectedBatchIndex > -1) {
                                break;
                            }
                            index++;
                        }
                    }
                }
                metric = new ServiceMetric(detectedBatchIndex);
            }
            return metric;
        }

        void exchange() {
            if (metric != null) {
                metric.exchangeWindows();
            }
        }

        void clear() {
            if (metric != null) {
                metric.clear();
            }
        }

        void toOutput(ReportWriter writer) {
            writer.writeMetricName(metricName);
            if (metric != null) {
                metric.toOutput(writer);
            }
        }
    }
}
