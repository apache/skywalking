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

package org.apache.skywalking.e2e.metrics;

import java.time.LocalDateTime;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.e2e.SimpleQueryClient;
import org.apache.skywalking.e2e.utils.Times;

@Slf4j
public class MetricsMatcher {

    public static void verifyMetrics(final SimpleQueryClient queryClient,
                                     final String metricName,
                                     final String id,
                                     final LocalDateTime minutesAgo) throws Exception {
        verifyMetrics(queryClient, metricName, id, minutesAgo, 0, null);
    }

    public static void verifyMetrics(final SimpleQueryClient queryClient,
                                     final String metricName,
                                     final String id,
                                     final LocalDateTime minutesAgo,
                                     final long retryInterval,
                                     final Runnable generateTraffic) throws Exception {
        boolean valid = false;
        while (!valid) {
            final Metrics metrics = queryClient.metrics(
                new MetricsQuery().stepByMinute()
                                  .metricsName(metricName)
                                  .start(minutesAgo)
                                  .end(Times.now().plusMinutes(1))
                                  .id(id));
            LOGGER.info("{}: {}", metricName, metrics);
            AtLeastOneOfMetricsMatcher instanceRespTimeMatcher = new AtLeastOneOfMetricsMatcher();
            MetricsValueMatcher greaterThanZero = new MetricsValueMatcher();
            greaterThanZero.setValue("gt 0");
            instanceRespTimeMatcher.setValue(greaterThanZero);
            try {
                instanceRespTimeMatcher.verify(metrics);
                valid = true;
            } catch (Throwable e) {
                if (generateTraffic != null) {
                    generateTraffic.run();
                    Thread.sleep(retryInterval);
                } else {
                    throw e;
                }
            }
        }
    }

    public static void verifyPercentileMetrics(final SimpleQueryClient queryClient,
                                               final String metricName,
                                               final String id,
                                               final LocalDateTime minutesAgo) throws Exception {
        verifyPercentileMetrics(queryClient, metricName, id, minutesAgo, 0, null);
    }

    public static void verifyPercentileMetrics(final SimpleQueryClient queryClient,
                                               final String metricName,
                                               final String id,
                                               final LocalDateTime minutesAgo,
                                               final long retryInterval,
                                               final Runnable generateTraffic) throws Exception {
        boolean valid = false;
        while (!valid) {
            final List<Metrics> metricsArray = queryClient.multipleLinearMetrics(
                new MetricsQuery().stepByMinute()
                                  .metricsName(metricName)
                                  .start(minutesAgo)
                                  .end(Times.now().plusMinutes(1))
                                  .id(id), "5");
            LOGGER.info("{}: {}", metricName, metricsArray);
            AtLeastOneOfMetricsMatcher matcher = new AtLeastOneOfMetricsMatcher();
            MetricsValueMatcher greaterThanZero = new MetricsValueMatcher();
            greaterThanZero.setValue("gt 0");
            matcher.setValue(greaterThanZero);
            try {
                metricsArray.forEach(matcher::verify);
                valid = true;
            } catch (Throwable e) {
                if (generateTraffic != null) {
                    generateTraffic.run();
                    Thread.sleep(retryInterval);
                } else {
                    throw e;
                }
            }
        }
    }
}
