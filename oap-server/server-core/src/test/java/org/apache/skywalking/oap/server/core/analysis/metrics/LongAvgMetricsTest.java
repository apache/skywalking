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

public class LongAvgMetricsTest {
    @Test
    public void testEntranceCombine() {
        LongAvgMetricsImpl impl = new LongAvgMetricsImpl();
        impl.combine(12, 1);
        impl.combine(24, 2);
        impl.combine(36, 3);
        impl.calculate();
        Assert.assertEquals(12, impl.getValue());
    }

    @Test
    public void testSelfCombine() {
        LongAvgMetricsImpl impl = new LongAvgMetricsImpl();
        impl.combine(12, 1);
        impl.combine(24, 2);

        LongAvgMetricsImpl impl2 = new LongAvgMetricsImpl();
        impl2.combine(24, 1);
        impl2.combine(48, 2);

        impl.combine(impl2);

        impl.calculate();
        Assert.assertEquals(18, impl.getValue());
    }

    public class LongAvgMetricsImpl extends LongAvgMetrics {

        @Override
        public String id() {
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
}
