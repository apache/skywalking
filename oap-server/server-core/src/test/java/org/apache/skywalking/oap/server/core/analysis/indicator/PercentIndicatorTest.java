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

import org.apache.skywalking.oap.server.core.analysis.indicator.expression.EqualMatch;
import org.apache.skywalking.oap.server.core.remote.grpc.proto.RemoteData;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author wusheng
 */
public class PercentIndicatorTest {
    @Test
    public void testEntranceCombine() {
        PercentIndicatorImpl impl = new PercentIndicatorImpl();
        impl.combine(new EqualMatch(), true, true);
        impl.combine(new EqualMatch(), true, false);
        impl.combine(new EqualMatch(), true, false);

        impl.calculate();

        Assert.assertEquals(3333, impl.getValue());

        impl = new PercentIndicatorImpl();
        impl.combine(new EqualMatch(), true, true);
        impl.combine(new EqualMatch(), true, true);
        impl.combine(new EqualMatch(), true, false);

        impl.calculate();

        Assert.assertEquals(6666, impl.getValue());
    }

    @Test
    public void testSelfCombine() {
        PercentIndicatorImpl impl = new PercentIndicatorImpl();
        impl.combine(new EqualMatch(), true, true);
        impl.combine(new EqualMatch(), true, false);
        impl.combine(new EqualMatch(), true, false);

        PercentIndicatorImpl impl2 = new PercentIndicatorImpl();
        impl2.combine(new EqualMatch(), true, true);
        impl2.combine(new EqualMatch(), true, true);
        impl2.combine(new EqualMatch(), true, false);

        impl.combine(impl2);

        impl.calculate();

        Assert.assertEquals(5000, impl.getValue());
    }

    public class PercentIndicatorImpl extends PercentIndicator {

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
