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

package org.apache.skywalking.oap.server.core.analysis.indicator;

import java.util.Map;
import org.apache.skywalking.oap.server.core.remote.grpc.proto.RemoteData;
import org.junit.Assert;
import org.junit.Test;
import org.powermock.reflect.Whitebox;

/**
 * @author wusheng
 */
public class ThermodynamicIndicatorTest {
    private int step = 10;//ms
    private int maxNumOfSteps = 10;//count

    @Test
    public void testEntrance() {
        ThermodynamicIndicatorTest.ThermodynamicIndicatorMocker indicatorMocker = new ThermodynamicIndicatorTest.ThermodynamicIndicatorMocker();

        indicatorMocker.combine(2000, step, maxNumOfSteps);
        indicatorMocker.combine(110, step, maxNumOfSteps);
        indicatorMocker.combine(100, step, maxNumOfSteps);
        indicatorMocker.combine(100, step, maxNumOfSteps);
        indicatorMocker.combine(100, step, maxNumOfSteps);
        indicatorMocker.combine(50, step, maxNumOfSteps);
        indicatorMocker.combine(50, step, maxNumOfSteps);
        indicatorMocker.combine(28, step, maxNumOfSteps);
        indicatorMocker.combine(50, step, maxNumOfSteps);
        indicatorMocker.combine(61, step, maxNumOfSteps);
        indicatorMocker.combine(100, step, maxNumOfSteps);
        indicatorMocker.combine(100, step, maxNumOfSteps);
        indicatorMocker.combine(100, step, maxNumOfSteps);

        Map<Integer, IntKeyLongValue> index = Whitebox.getInternalState(indicatorMocker, "detailIndex");
        Assert.assertEquals(4, index.size());

        Assert.assertEquals(1, index.get(2).getValue());
        Assert.assertEquals(3, index.get(5).getValue());
        Assert.assertEquals(1, index.get(6).getValue());
        Assert.assertEquals(8, index.get(10).getValue());
    }

    @Test
    public void testMerge() {
        ThermodynamicIndicatorTest.ThermodynamicIndicatorMocker indicatorMocker = new ThermodynamicIndicatorTest.ThermodynamicIndicatorMocker();

        indicatorMocker.combine(2000, step, maxNumOfSteps);
        indicatorMocker.combine(110, step, maxNumOfSteps);
        indicatorMocker.combine(100, step, maxNumOfSteps);
        indicatorMocker.combine(100, step, maxNumOfSteps);
        indicatorMocker.combine(100, step, maxNumOfSteps);
        indicatorMocker.combine(50, step, maxNumOfSteps);
        indicatorMocker.combine(50, step, maxNumOfSteps);

        ThermodynamicIndicatorTest.ThermodynamicIndicatorMocker indicatorMocker2 = new ThermodynamicIndicatorTest.ThermodynamicIndicatorMocker();

        indicatorMocker2.combine(28, step, maxNumOfSteps);
        indicatorMocker2.combine(50, step, maxNumOfSteps);
        indicatorMocker2.combine(61, step, maxNumOfSteps);
        indicatorMocker2.combine(100, step, maxNumOfSteps);
        indicatorMocker2.combine(100, step, maxNumOfSteps);
        indicatorMocker2.combine(100, step, maxNumOfSteps);

        indicatorMocker.combine(indicatorMocker2);

        Map<Integer, IntKeyLongValue> index = Whitebox.getInternalState(indicatorMocker, "detailIndex");
        Assert.assertEquals(4, index.size());

        Assert.assertEquals(1, index.get(2).getValue());
        Assert.assertEquals(3, index.get(5).getValue());
        Assert.assertEquals(1, index.get(6).getValue());
        Assert.assertEquals(8, index.get(10).getValue());
    }

    public class ThermodynamicIndicatorMocker extends ThermodynamicIndicator {

        @Override public String id() {
            return null;
        }

        @Override public Indicator toHour() {
            return null;
        }

        @Override public Indicator toDay() {
            return null;
        }

        @Override public Indicator toMonth() {
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
