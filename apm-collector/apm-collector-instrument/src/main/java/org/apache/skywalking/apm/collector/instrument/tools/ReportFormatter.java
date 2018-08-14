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

package org.apache.skywalking.apm.collector.instrument.tools;

import java.util.*;
import org.slf4j.*;

/**
 * @author peng-yongsheng
 */
class ReportFormatter {

    private final Logger logger = LoggerFactory.getLogger(ReportFormatter.class);

    private Map<String, Metric> metricMap = new LinkedHashMap<>();

    void format(Report report) {
        logger.info(System.lineSeparator() + "Formatted report: ");

        report.getMetrics().forEach(metric -> {
            if (metricMap.containsKey(metric.getMetricName())) {
                Metric existMetric = metricMap.get(metric.getMetricName());
                existMetric.setTotal(existMetric.getTotal() + metric.getTotal());
                existMetric.setCalls(existMetric.getCalls() + metric.getCalls());
                existMetric.setAvg(existMetric.getTotal() / existMetric.getCalls());
            } else {
                metricMap.put(metric.getMetricName(), metric);
            }
        });

        logger.info("");

        metricMap.values().forEach(metric ->
            logger.info("metric name: {}, avg: {}, rate: {}, calls: {}, total: {}",
                metric.getMetricName(),
                metric.getAvg(),
                metric.getRate(),
                metric.getCalls(),
                metric.getTotalWithUnit()));
    }
}
