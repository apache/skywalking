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
import org.apache.skywalking.oap.server.core.storage.StorageID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class CPM5DecimalsMetricsTest {

    @Test
    public void testEntranceCombine() {
        CPM5DecimalsMetricsTest.CountMetricsImpl impl = new CountMetricsImpl();
        impl.combine(5);
        impl.combine(6);
        impl.combine(7);

        impl.setTimeBucket(202302201608L);
        impl.calculate();

        Assertions.assertEquals(1800000, impl.getValue());
    }

    @Test
    public void testSelfCombine() {
        CPM5DecimalsMetricsTest.CountMetricsImpl impl = new CountMetricsImpl();

        impl.combine(5);
        impl.combine(6);
        impl.combine(7);

        CPM5DecimalsMetricsTest.CountMetricsImpl impl2 = new CountMetricsImpl();
        impl2.combine(5);
        impl2.combine(6);
        impl2.combine(7);

        impl.setTimeBucket(202302201608L);
        impl.combine(impl2);

        impl.calculate();

        Assertions.assertEquals(3600000, impl.getValue());
    }

    @Test
    public void testDownSampleCombine() {
        CPM5DecimalsMetricsTest.CountMetricsImpl impl = new CountMetricsImpl();
        impl.setTimeBucket(202302201608L);
        impl.combine(1);

        impl.calculate();

        Assertions.assertEquals(100000, impl.getValue());

        impl.setTimeBucket(impl.toTimeBucketInHour());
        impl.calculate();

        impl.setTimeBucket(impl.toTimeBucketInDay());
        impl.calculate();

        Assertions.assertEquals(69, impl.getValue());
    }

    private static class CountMetricsImpl extends CPM5DecimalsMetrics {
        @Override
        public Metrics toHour() {
            return null;
        }

        @Override
        public Metrics toDay() {
            return null;
        }

        @Override
        protected StorageID id0() {
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