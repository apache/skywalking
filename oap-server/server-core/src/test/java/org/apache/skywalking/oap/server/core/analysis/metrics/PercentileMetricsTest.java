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

import java.util.Iterator;
import org.apache.skywalking.oap.server.core.remote.grpc.proto.DataIntLongPairList;
import org.apache.skywalking.oap.server.core.remote.grpc.proto.IntKeyLongValuePair;
import org.apache.skywalking.oap.server.core.remote.grpc.proto.RemoteData;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author wusheng
 */
public class PercentileMetricsTest {
    private int precision = 10;//ms

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

        Assert.assertArrayEquals(new int[] {70, 90, 90, 90, 110}, metricsMocker.getValues());
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

        Assert.assertArrayEquals(new int[] {90, 90, 90, 90, 90}, metricsMocker.getValues());
    }

    @Test
    public void percentileTest3() {
        PercentileMetricsTest.PercentileMetricsMocker metricsMocker = new PercentileMetricsTest.PercentileMetricsMocker();

        metricsMocker.combine(90, precision);
        metricsMocker.combine(110, precision);

        metricsMocker.calculate();

        RemoteData.Builder serialize = metricsMocker.serialize();
        metricsMocker = new PercentileMetricsTest.PercentileMetricsMocker();
        metricsMocker.deserialize(serialize.build());

        metricsMocker.calculate();

        Assert.assertArrayEquals(new int[] {90, 110, 110, 110, 110}, metricsMocker.getValues());
    }

    @Test
    public void percentileTest4() {
        PercentileMetricsTest.PercentileMetricsMocker metricsMocker = new PercentileMetricsTest.PercentileMetricsMocker();

        metricsMocker.combine(0, precision);
        metricsMocker.combine(0, precision);

        metricsMocker.calculate();

        Assert.assertArrayEquals(new int[] {0, 0, 0, 0, 0}, metricsMocker.getValues());
    }

    public class PercentileMetricsMocker extends PercentileMetrics {

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

        @Override public int remoteHashCode() {
            return 0;
        }

        public RemoteData.Builder serialize() {
            RemoteData.Builder var1 = RemoteData.newBuilder();
            var1.addDataLongs(this.getTimeBucket());
            var1.addDataIntegers(this.getPrecision());
            Iterator var2 = getPercentileValues().values().iterator();
            org.apache.skywalking.oap.server.core.remote.grpc.proto.DataIntLongPairList.Builder var3 = DataIntLongPairList.newBuilder();

            while(var2.hasNext()) {
                var3.addValue(((IntKeyLongValue)var2.next()).serialize());
            }

            var1.addDataLists(var3);
            var2 = getDataset().values().iterator();
            var3 = DataIntLongPairList.newBuilder();

            while(var2.hasNext()) {
                var3.addValue(((IntKeyLongValue)var2.next()).serialize());
            }

            var1.addDataLists(var3);
            return var1;
        }

        public void deserialize(RemoteData var1) {
            this.setTimeBucket(var1.getDataLongs(0));
            this.setPrecision(var1.getDataIntegers(0));
            Iterator var2 = var1.getDataLists(0).getValueList().iterator();

            while(var2.hasNext()) {
                IntKeyLongValuePair var3 = (IntKeyLongValuePair)var2.next();
                getPercentileValues().put(new Integer(var3.getKey()), new IntKeyLongValue(var3.getKey(), var3.getValue()));
            }

            var2 = var1.getDataLists(1).getValueList().iterator();

            while(var2.hasNext()) {
                IntKeyLongValuePair var4 = (IntKeyLongValuePair)var2.next();
                getDataset().put(new Integer(var4.getKey()), new IntKeyLongValue(var4.getKey(), var4.getValue()));
            }

        }
    }
}
