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

import org.apache.skywalking.oap.server.core.remote.grpc.proto.RemoteData;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author wusheng
 */
public class PxxIndicatorTest {
    private int precision = 10;//ms

    @Test
    public void p99Test() {
        PxxIndicatorMocker indicatorMocker = new PxxIndicatorMocker(99);

        indicatorMocker.combine(110, precision);
        indicatorMocker.combine(100, precision);
        indicatorMocker.combine(100, precision);
        indicatorMocker.combine(100, precision);
        indicatorMocker.combine(50, precision);
        indicatorMocker.combine(50, precision);
        indicatorMocker.combine(50, precision);
        indicatorMocker.combine(61, precision);
        indicatorMocker.combine(100, precision);
        indicatorMocker.combine(100, precision);
        indicatorMocker.combine(100, precision);

        indicatorMocker.calculate();

        Assert.assertEquals(110, indicatorMocker.getValue());
    }

    @Test
    public void p75Test() {
        PxxIndicatorMocker indicatorMocker = new PxxIndicatorMocker(75);

        indicatorMocker.combine(110, precision);
        indicatorMocker.combine(100, precision);
        indicatorMocker.combine(100, precision);
        indicatorMocker.combine(100, precision);
        indicatorMocker.combine(50, precision);
        indicatorMocker.combine(50, precision);
        indicatorMocker.combine(50, precision);
        indicatorMocker.combine(61, precision);
        indicatorMocker.combine(61, precision);
        indicatorMocker.combine(71, precision);
        indicatorMocker.combine(100, precision);

        indicatorMocker.calculate();

        // precision = 10, 71 ~= 70
        Assert.assertEquals(100, indicatorMocker.getValue());
    }

    @Test
    public void p50Test() {
        PxxIndicatorMocker indicatorMocker = new PxxIndicatorMocker(50);

        indicatorMocker.combine(110, precision);
        indicatorMocker.combine(100, precision);
        indicatorMocker.combine(100, precision);
        indicatorMocker.combine(100, precision);
        indicatorMocker.combine(50, precision);
        indicatorMocker.combine(50, precision);
        indicatorMocker.combine(50, precision);
        indicatorMocker.combine(61, precision);
        indicatorMocker.combine(61, precision);
        indicatorMocker.combine(71, precision);
        indicatorMocker.combine(100, precision);

        indicatorMocker.calculate();

        // precision = 10, 71 ~= 70
        Assert.assertEquals(70, indicatorMocker.getValue());
    }

    public class PxxIndicatorMocker extends PxxIndicator {

        public PxxIndicatorMocker(int percentileRank) {
            super(percentileRank);
        }

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
