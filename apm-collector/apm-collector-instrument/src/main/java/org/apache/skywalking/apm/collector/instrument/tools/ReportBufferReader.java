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

import java.io.BufferedReader;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
class ReportBufferReader {

    private final Logger logger = LoggerFactory.getLogger(ReportBufferReader.class);

    Report read(BufferedReader bufferedReader) throws IOException {
        Report report = new Report();

        String line;
        while ((line = bufferedReader.readLine()) != null) {
            if (line.startsWith(":end")) {
                break;
            }

            if (!line.startsWith("/") && !line.startsWith("\t")) {
                continue;
            }

            Metric metric = new Metric();
            metric.setMetricName(line);
            new MetricReader(bufferedReader).read(metric);

            logger.info("metric name: {}, avg: {}, rate: {}, calls: {}, total: {}",
                metric.getMetricName(),
                metric.getAvg(),
                metric.getRate(),
                metric.getCalls(),
                metric.getTotal());

            report.addMetric(metric);
        }

        return report;
    }
}
