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

package org.apache.skywalking.oap.server.library.util.prometheus.parser;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.Queue;
import org.apache.skywalking.oap.server.library.util.prometheus.metrics.Counter;
import org.apache.skywalking.oap.server.library.util.prometheus.metrics.Histogram;
import org.apache.skywalking.oap.server.library.util.prometheus.metrics.MetricFamily;
import org.apache.skywalking.oap.server.library.util.prometheus.metrics.MetricType;
import org.apache.skywalking.oap.server.library.util.prometheus.metrics.Summary;
import org.apache.skywalking.oap.server.library.util.ResourceUtils;
import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

public class TextParserTest {

    Queue<MetricFamily> expectedMfs = new LinkedList<>();

    @Before
    public void setup() {
        expectedMfs.offer(new MetricFamily.Builder()
            .setName("http_requests_total")
                              .setType(MetricType.COUNTER)
                              .setHelp("The total number of HTTP requests.")
                              .addMetric(Counter.builder()
                                                .name("http_requests_total")
                                                .label("method", "post")
                                                .label("code", "200")
                                                .value(1027D)
                                                .build())
                              .addMetric(Counter.builder()
                                                .name("http_requests_total")
                                                .label("method", "post")
                                                .label("code", "400")
                                                .value(3D)
                                                .build())
                              .build());
        expectedMfs.offer(new MetricFamily.Builder()
                              .setName("http_request_duration_seconds")
                              .setType(MetricType.HISTOGRAM)
                              .setHelp("A histogram of the request duration.")
                              .addMetric(Histogram.builder()
                                                  .name("http_request_duration_seconds")
                                                  .sampleCount(144320L)
                                                  .sampleSum(53423.0D)
                                                  .bucket(0.05D, 24054L)
                                                  .bucket(0.1D, 33444L)
                                                  .bucket(0.2D, 100392L)
                                                  .bucket(0.5D, 129389L)
                                                  .bucket(1.0D, 133988L)
                                                  .bucket(Double.POSITIVE_INFINITY, 144320L)
                                                  .build())
                              .build());
        expectedMfs.offer(new MetricFamily.Builder()
                              .setName("rpc_duration_seconds")
                              .setType(MetricType.SUMMARY)
                              .setHelp("A summary of the RPC duration in seconds.")
                              .addMetric(Summary.builder()
                                             .name("rpc_duration_seconds")
                                             .sampleCount(2693L)
                                             .sampleSum(1.7560473E7D)
                                             .quantile(0.01D, 3102D)
                                             .quantile(0.05D, 3272D)
                                             .quantile(0.5D, 4773D)
                                             .quantile(0.9D, 9001D)
                                             .quantile(0.99D, 76656D)
                                             .build())
                              .build());
    }

    @Test
    public void parseTextSuccessfully() throws IOException {
        try (InputStream is = ResourceUtils.readToStream("testdata/prometheus.txt")) {
            TextParser parser = new TextParser(is);
            MetricFamily mf;
            int mfNum = 0;
            while ((mf = parser.parse()) != null) {
                mfNum++;
                MetricFamily expected = expectedMfs.poll();
                assertNotNull(expected);
                assertThat(mf, is(expected));
            }
            assertThat(mfNum , is(3));
        }
    }
}