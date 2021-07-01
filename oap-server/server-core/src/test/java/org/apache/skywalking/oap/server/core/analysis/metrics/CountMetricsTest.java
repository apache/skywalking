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

public class CountMetricsTest {
    @Test
    public void testEntranceCombine() {
        CountMetricsImpl impl = new CountMetricsImpl();
        impl.combine(5);
        impl.combine(6);
        impl.combine(7);

        impl.calculate();

        Assert.assertEquals(18, impl.getValue());
    }

    @Test
    public void testSelfCombine() {
        CountMetricsImpl impl = new CountMetricsImpl();
        impl.combine(5);
        impl.combine(6);
        impl.combine(7);

        CountMetricsImpl impl2 = new CountMetricsImpl();
        impl2.combine(5);
        impl2.combine(6);
        impl2.combine(7);

        impl.combine(impl2);

        impl.calculate();

        Assert.assertEquals(36, impl.getValue());
    }

    public class CountMetricsImpl extends CountMetrics {
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
}
