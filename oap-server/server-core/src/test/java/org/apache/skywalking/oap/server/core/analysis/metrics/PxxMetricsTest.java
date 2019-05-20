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

package org.apache.skywalking.oap.server.core.analysis.metrics;

import org.apache.skywalking.oap.server.core.remote.grpc.proto.RemoteData;
import org.junit.*;

/**
 * @author wusheng
 */
public class PxxMetricsTest {
    private int precision = 10;//ms

    @Test
    public void p99Test() {
        PxxMetricsMocker metricsMocker = new PxxMetricsMocker(99);

        metricsMocker.combine(110, precision);
        metricsMocker.combine(100, precision);
        metricsMocker.combine(100, precision);
        metricsMocker.combine(100, precision);
        metricsMocker.combine(50, precision);
        metricsMocker.combine(50, precision);
        metricsMocker.combine(50, precision);
        metricsMocker.combine(61, precision);
        metricsMocker.combine(100, precision);
        metricsMocker.combine(100, precision);
        metricsMocker.combine(100, precision);

        metricsMocker.calculate();

        Assert.assertEquals(110, metricsMocker.getValue());
    }

    @Test
    public void p75Test() {
        PxxMetricsMocker metricsMocker = new PxxMetricsMocker(75);

        metricsMocker.combine(110, precision);
        metricsMocker.combine(100, precision);
        metricsMocker.combine(100, precision);
        metricsMocker.combine(100, precision);
        metricsMocker.combine(50, precision);
        metricsMocker.combine(50, precision);
        metricsMocker.combine(50, precision);
        metricsMocker.combine(61, precision);
        metricsMocker.combine(61, precision);
        metricsMocker.combine(71, precision);
        metricsMocker.combine(100, precision);

        metricsMocker.calculate();

        // precision = 10, 71 ~= 70
        Assert.assertEquals(100, metricsMocker.getValue());
    }

    @Test
    public void p50Test() {
        PxxMetricsMocker metricsMocker = new PxxMetricsMocker(50);

        metricsMocker.combine(110, precision);
        metricsMocker.combine(100, precision);
        metricsMocker.combine(100, precision);
        metricsMocker.combine(100, precision);
        metricsMocker.combine(50, precision);
        metricsMocker.combine(50, precision);
        metricsMocker.combine(50, precision);
        metricsMocker.combine(61, precision);
        metricsMocker.combine(61, precision);
        metricsMocker.combine(71, precision);
        metricsMocker.combine(100, precision);

        metricsMocker.calculate();

        // precision = 10, 71 ~= 70
        Assert.assertEquals(70, metricsMocker.getValue());
    }

    public class PxxMetricsMocker extends PxxMetrics {

        public PxxMetricsMocker(int percentileRank) {
            super(percentileRank);
        }

        @Override public String id() {
            return null;
        }

        @Override public Metrics toHour() {
            return null;
        }

        @Override public Metrics toDay() {
            return null;
        }

        @Override public Metrics toMonth() {
            return null;
        }

        @Override public void deserialize(RemoteData remoteData) {

        }

        @Override public RemoteData.Builder serialize() {
            return null;
        }

        @Override public int remoteHashCode() {
            return 0;
        }
    }
}
