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
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.apache.skywalking.apm.collector.core.annotations.trace.BatchParameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author wusheng
 */
public enum MetricTree implements Runnable {
    INSTANCE;
    private final Logger logger = LoggerFactory.getLogger(MetricTree.class);

    private MetricNode root = new MetricNode("/", "/");
    private ScheduledFuture<?> scheduledFuture;
    private String lineSeparator = System.getProperty("line.separator");

    MetricTree() {
        ScheduledExecutorService service = Executors.newSingleThreadScheduledExecutor();
        scheduledFuture = service.scheduleAtFixedRate(this, 60, 60, TimeUnit.SECONDS);
    }

    synchronized MetricNode lookup(String metricName) {
        String[] metricSections = metricName.split("/");
        MetricNode node = root;
        for (String metricSection : metricSections) {
            node = node.addChild(metricSection, metricName);
        }
        return node;
    }

    @Override
    public void run() {
        root.exchange();

        try {
            Thread.sleep(5 * 1000);
        } catch (InterruptedException e) {

        }

        StringBuilder logBuffer = new StringBuilder();
        logBuffer.append(lineSeparator);
        logBuffer.append("##################################################################################################################").append(lineSeparator);
        logBuffer.append("#                                             Collector Service Report                                           #").append(lineSeparator);
        logBuffer.append("##################################################################################################################").append(lineSeparator);
        root.toOutput(new ReportWriter() {
            private int stackDepth = 0;

            @Override public void writeMetricName(String name) {
                for (int i = 0; i < stackDepth; i++) {
                    logBuffer.append("\t");
                }
                logBuffer.append(name).append("").append(lineSeparator);
            }

            @Override public void writeMetric(String metrics) {
                for (int i = 0; i < stackDepth; i++) {
                    logBuffer.append("\t");
                }
                logBuffer.append("\t");
                logBuffer.append(metrics).append("").append(lineSeparator);
            }

            @Override public void prepare4Child() {
                stackDepth++;
            }

            @Override public void finished() {
                stackDepth--;
            }
        });

        logger.warn(logBuffer.toString());
    }

    class MetricNode {
        private String nodeName;
        private String metricName;
        private volatile ServiceMetric metric;
        private List<MetricNode> childs = new LinkedList<>();

        public MetricNode(String nodeName, String metricName) {
            this.nodeName = nodeName;
            this.metricName = metricName;

        }

        ServiceMetric getMetric(Method targetMethod, Object[] allArguments) {
            if (metric == null) {
                synchronized (nodeName) {
                    if (metric == null) {
                        boolean isBatchDetected = false;
                        if (targetMethod != null) {
                            Annotation[][] annotations = targetMethod.getParameterAnnotations();
                            if (annotations != null) {
                                int index = 0;
                                for (Annotation[] parameterAnnotation : annotations) {
                                    if (parameterAnnotation != null) {
                                        for (Annotation annotation : parameterAnnotation) {
                                            if (annotation.equals(BatchParameter.class)) {
                                                isBatchDetected = true;
                                                break;
                                            }
                                        }
                                    }
                                    index++;
                                }
                                if (isBatchDetected) {
                                    Object listArgs = allArguments[index];

                                    if (listArgs instanceof List) {
                                        List args = (List)listArgs;
                                        metricName += "/" + args.get(0).getClass().getSimpleName();
                                    }
                                }
                            }
                        }
                        metric = new ServiceMetric(metricName, isBatchDetected);
                    }
                }
            }
            return metric;
        }

        MetricNode addChild(String nodeName, String metricName) {
            MetricNode childNode = new MetricNode(nodeName, metricName);
            this.childs.add(childNode);
            return childNode;
        }

        void exchange() {
            if (metric != null) {
                metric.exchangeWindows();
            }
            if (childs.size() > 0) {
                for (MetricNode child : childs) {
                    child.exchange();
                }
            }
        }

        void toOutput(ReportWriter writer) {
            if (!nodeName.equals("/")) {
                writer.writeMetricName(nodeName);
                if (metric != null) {
                    metric.toOutput(writer);
                }
                if (childs.size() > 0) {
                    for (MetricNode child : childs) {
                        writer.prepare4Child();
                        child.toOutput(writer);
                        writer.finished();
                    }
                }
            } else {
                writer.writeMetricName("/");
                if (childs.size() > 0) {
                    for (MetricNode child : childs) {
                        child.toOutput(writer);
                    }
                }
            }


        }
    }
}
