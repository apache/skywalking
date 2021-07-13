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

public class PxxMetricsTest {
    private int precision = 10; //ms

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
        public void deserialize(RemoteData remoteData) {

        }

        @Override
        public RemoteData.Builder serialize() {
            return null;
        }

        @Override
        public int remoteHashCode() {
            return 0;
        }
    }

    @Test
    public void testAccurate() {
        DataTable map = new DataTable();
        map.toObject("0,109|128,3|130,1|131,1|132,2|5,16|6,23|10,1|12,1|13,25|14,10|15,2|17,1|146,2|18,1|19,16|20,9|21,4|22,1|23,2|152,1|25,4|26,4|27,3|28,1|31,1|32,2|34,1|44,1|318,1|319,7|320,2|321,1|323,1|324,1|325,2|326,1|327,3|328,1|330,2|205,27|206,14|208,1|337,1|219,15|220,2|221,2|222,1|224,1|352,1|225,1|226,3|227,1|229,1|232,2|105,16|233,1|106,13|108,1|113,20|114,4|115,3|116,2|118,6|119,12|120,4|121,4|122,6|250,1|124,4|125,1|126,4|127,2");

        PxxMetricsMocker metrics50Mocker = new PxxMetricsMocker(50);
        metrics50Mocker.setDetailGroup(map);
        metrics50Mocker.setPrecision(10);
        metrics50Mocker.calculate();
        int p50 = metrics50Mocker.getValue();

        PxxMetricsMocker metrics75Mocker = new PxxMetricsMocker(75);
        metrics75Mocker.setDetailGroup(map);
        metrics75Mocker.setPrecision(10);
        metrics75Mocker.calculate();
        int p75 = metrics75Mocker.getValue();

        PxxMetricsMocker metrics90Mocker = new PxxMetricsMocker(90);
        metrics90Mocker.setDetailGroup(map);
        metrics90Mocker.setPrecision(10);
        metrics90Mocker.calculate();
        int p90 = metrics90Mocker.getValue();

        PxxMetricsMocker metrics95Mocker = new PxxMetricsMocker(95);
        metrics95Mocker.setDetailGroup(map);
        metrics95Mocker.setPrecision(10);
        metrics95Mocker.calculate();
        int p95 = metrics95Mocker.getValue();

        PxxMetricsMocker metrics99Mocker = new PxxMetricsMocker(99);
        metrics99Mocker.setDetailGroup(map);
        metrics99Mocker.setPrecision(10);
        metrics99Mocker.calculate();
        int p99 = metrics99Mocker.getValue();

        Assert.assertTrue(p50 < p75);
        Assert.assertTrue(p75 < p90);
        Assert.assertTrue(p90 < p95);
        Assert.assertTrue(p95 < p99);
    }
}
