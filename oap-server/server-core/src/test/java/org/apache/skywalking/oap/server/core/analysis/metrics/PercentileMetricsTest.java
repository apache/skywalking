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
import org.junit.Assert;
import org.junit.Test;

public class PercentileMetricsTest {
    private int precision = 10; //ms

    @Test
    public void percentileTest() {
        PercentileMetricsTest.PercentileMetricsMocker metricsMocker = new PercentileMetricsTest.PercentileMetricsMocker();

        metricsMocker.combine(110, precision);
        metricsMocker.combine(90, precision);
        metricsMocker.combine(95, precision);
        metricsMocker.combine(99, precision);
        metricsMocker.combine(50, precision);
        metricsMocker.combine(50, precision);
        metricsMocker.combine(50, precision);
        metricsMocker.combine(50, precision);
        metricsMocker.combine(50, precision);
        metricsMocker.combine(75, precision);
        metricsMocker.combine(75, precision);

        metricsMocker.calculate();

        Assert.assertArrayEquals(new int[] {
            70,
            90,
            90,
            90,
            110
        }, metricsMocker.getValues());
    }

    @Test
    public void percentileTest2() {
        PercentileMetricsTest.PercentileMetricsMocker metricsMocker = new PercentileMetricsTest.PercentileMetricsMocker();

        metricsMocker.combine(90, precision);
        metricsMocker.combine(90, precision);
        metricsMocker.combine(90, precision);
        metricsMocker.combine(90, precision);
        metricsMocker.combine(90, precision);
        metricsMocker.combine(90, precision);
        metricsMocker.combine(90, precision);
        metricsMocker.combine(90, precision);
        metricsMocker.combine(90, precision);
        metricsMocker.combine(90, precision);
        metricsMocker.combine(90, precision);

        metricsMocker.calculate();

        Assert.assertArrayEquals(new int[] {
            90,
            90,
            90,
            90,
            90
        }, metricsMocker.getValues());
    }

    @Test
    public void percentileTest3() {
        PercentileMetricsTest.PercentileMetricsMocker metricsMocker = new PercentileMetricsTest.PercentileMetricsMocker();

        metricsMocker.combine(90, precision);
        metricsMocker.combine(110, precision);

        metricsMocker.calculate();

        Assert.assertArrayEquals(new int[] {
            90,
            110,
            110,
            110,
            110
        }, metricsMocker.getValues());
    }

    @Test
    public void percentileTest4() {
        PercentileMetricsTest.PercentileMetricsMocker metricsMocker = new PercentileMetricsTest.PercentileMetricsMocker();

        metricsMocker.combine(0, precision);
        metricsMocker.combine(0, precision);

        metricsMocker.calculate();

        Assert.assertArrayEquals(new int[] {
            0,
            0,
            0,
            0,
            0
        }, metricsMocker.getValues());
    }

    public class PercentileMetricsMocker extends PercentileMetrics {

        @Override
        protected String id0() {
            return null;
        }

        @Override
        public Metrics toHour() {
            return null;
        }

        @Override
        public Metrics toDay() {
            return null;
        }

        @Override
        public int remoteHashCode() {
            return 0;
        }

        @Override
        public void deserialize(RemoteData remoteData) {

        }

        @Override
        public RemoteData.Builder serialize() {
            return null;
        }
    }
}
