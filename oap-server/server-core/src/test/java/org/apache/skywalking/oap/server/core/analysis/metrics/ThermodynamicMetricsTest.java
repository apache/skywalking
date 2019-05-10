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

import java.util.Map;
import org.apache.skywalking.oap.server.core.remote.grpc.proto.RemoteData;
import org.junit.Assert;
import org.junit.Test;
import org.powermock.reflect.Whitebox;

/**
 * @author wusheng
 */
public class ThermodynamicMetricsTest {
    private int step = 10;//ms
    private int maxNumOfSteps = 10;//count

    @Test
    public void testEntrance() {
        ThermodynamicMetricsMocker metricsMocker = new ThermodynamicMetricsMocker();

        metricsMocker.combine(2000, step, maxNumOfSteps);
        metricsMocker.combine(110, step, maxNumOfSteps);
        metricsMocker.combine(100, step, maxNumOfSteps);
        metricsMocker.combine(100, step, maxNumOfSteps);
        metricsMocker.combine(100, step, maxNumOfSteps);
        metricsMocker.combine(50, step, maxNumOfSteps);
        metricsMocker.combine(50, step, maxNumOfSteps);
        metricsMocker.combine(28, step, maxNumOfSteps);
        metricsMocker.combine(50, step, maxNumOfSteps);
        metricsMocker.combine(61, step, maxNumOfSteps);
        metricsMocker.combine(100, step, maxNumOfSteps);
        metricsMocker.combine(100, step, maxNumOfSteps);
        metricsMocker.combine(100, step, maxNumOfSteps);

        Map<Integer, IntKeyLongValue> index = Whitebox.getInternalState(metricsMocker, "detailIndex");
        Assert.assertEquals(4, index.size());

        Assert.assertEquals(1, index.get(2).getValue());
        Assert.assertEquals(3, index.get(5).getValue());
        Assert.assertEquals(1, index.get(6).getValue());
        Assert.assertEquals(8, index.get(10).getValue());
    }

    @Test
    public void testMerge() {
        ThermodynamicMetricsMocker metricsMocker = new ThermodynamicMetricsMocker();

        metricsMocker.combine(2000, step, maxNumOfSteps);
        metricsMocker.combine(110, step, maxNumOfSteps);
        metricsMocker.combine(100, step, maxNumOfSteps);
        metricsMocker.combine(100, step, maxNumOfSteps);
        metricsMocker.combine(100, step, maxNumOfSteps);
        metricsMocker.combine(50, step, maxNumOfSteps);
        metricsMocker.combine(50, step, maxNumOfSteps);

        ThermodynamicMetricsMocker metricsMocker1 = new ThermodynamicMetricsMocker();

        metricsMocker1.combine(28, step, maxNumOfSteps);
        metricsMocker1.combine(50, step, maxNumOfSteps);
        metricsMocker1.combine(61, step, maxNumOfSteps);
        metricsMocker1.combine(100, step, maxNumOfSteps);
        metricsMocker1.combine(100, step, maxNumOfSteps);
        metricsMocker1.combine(100, step, maxNumOfSteps);

        metricsMocker.combine(metricsMocker1);

        Map<Integer, IntKeyLongValue> index = Whitebox.getInternalState(metricsMocker, "detailIndex");
        Assert.assertEquals(4, index.size());

        Assert.assertEquals(1, index.get(2).getValue());
        Assert.assertEquals(3, index.get(5).getValue());
        Assert.assertEquals(1, index.get(6).getValue());
        Assert.assertEquals(8, index.get(10).getValue());
    }

    public class ThermodynamicMetricsMocker extends ThermodynamicMetrics {

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
