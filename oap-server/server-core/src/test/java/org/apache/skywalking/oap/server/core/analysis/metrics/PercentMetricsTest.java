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

import org.apache.skywalking.oap.server.core.analysis.metrics.expression.StringMatch;
import org.apache.skywalking.oap.server.core.remote.grpc.proto.RemoteData;
import org.apache.skywalking.oap.server.core.storage.StorageID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class PercentMetricsTest {
    @Test
    public void testEntranceCombine() {
        PercentMetricsImpl impl = new PercentMetricsImpl();
        impl.combine(new StringMatch().match(true, true));
        impl.combine(new StringMatch().match(true, false));
        impl.combine(new StringMatch().match(true, false));

        impl.calculate();

        Assertions.assertEquals(3333, impl.getValue());

        impl = new PercentMetricsImpl();
        impl.combine(new StringMatch().match(true, true));
        impl.combine(new StringMatch().match(true, true));
        impl.combine(new StringMatch().match(true, false));

        impl.calculate();

        Assertions.assertEquals(6666, impl.getValue());
    }

    @Test
    public void testSelfCombine() {
        PercentMetricsImpl impl = new PercentMetricsImpl();
        impl.combine(new StringMatch().match(true, true));
        impl.combine(new StringMatch().match(true, false));
        impl.combine(new StringMatch().match(true, false));

        PercentMetricsImpl impl2 = new PercentMetricsImpl();
        impl2.combine(new StringMatch().match(true, true));
        impl2.combine(new StringMatch().match(true, true));
        impl2.combine(new StringMatch().match(true, false));

        impl.combine(impl2);

        impl.calculate();

        Assertions.assertEquals(5000, impl.getValue());
    }

    public class PercentMetricsImpl extends PercentMetrics {

        @Override
        protected StorageID id0() {
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
