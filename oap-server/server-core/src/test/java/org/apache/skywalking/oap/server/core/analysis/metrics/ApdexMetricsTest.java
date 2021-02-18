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
import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

public class ApdexMetricsTest {

    @Before
    public void setUp() {
        ApdexMetrics.setDICT(name -> name.equals("foo") ? 500 : 1000);
    }

    @Test
    public void testEntrance() {
        ApdexMetrics apdex = new ApdexMetricsImpl();
        apdex.combine(200, "foo", true);
        apdex.calculate();
        assertThat(apdex.getValue(), is(10000));

        apdex = new ApdexMetricsImpl();
        apdex.combine(1000, "foo", true);
        apdex.calculate();
        assertThat(apdex.getValue(), is(5000));

        apdex = new ApdexMetricsImpl();
        apdex.combine(2000, "foo", true);
        apdex.calculate();
        assertThat(apdex.getValue(), is(5000));

        apdex = new ApdexMetricsImpl();
        apdex.combine(200, "foo", true);
        apdex.combine(300, "bar", true);
        apdex.calculate();
        assertThat(apdex.getValue(), is(10000));

        apdex = new ApdexMetricsImpl();
        apdex.combine(200, "foo", true);
        apdex.combine(1500, "bar", true);
        apdex.calculate();
        assertThat(apdex.getValue(), is(7500));

        apdex = new ApdexMetricsImpl();
        apdex.combine(200, "foo", true);
        apdex.combine(300, "bar", false);
        apdex.calculate();
        assertThat(apdex.getValue(), is(5000));

        apdex = new ApdexMetricsImpl();
        apdex.combine(200, "foo", true);
        apdex.combine(1500, "bar", false);
        apdex.calculate();
        assertThat(apdex.getValue(), is(5000));

        apdex = new ApdexMetricsImpl();
        apdex.combine(200, "foo", true);
        apdex.combine(5000, "bar", true);
        apdex.calculate();
        assertThat(apdex.getValue(), is(5000));
    }

    @Test
    public void testCombine() {
        ApdexMetrics apdex1 = new ApdexMetricsImpl();
        apdex1.combine(200, "foo", true);
        apdex1.combine(300, "bar", true);
        apdex1.combine(200, "foo", true);
        apdex1.combine(1500, "bar", true);

        ApdexMetrics apdex2 = new ApdexMetricsImpl();
        apdex2.combine(200, "foo", true);
        apdex2.combine(300, "bar", false);
        apdex2.combine(200, "foo", true);
        apdex2.combine(1500, "bar", false);
        apdex2.combine(200, "foo", true);
        apdex2.combine(5000, "bar", true);

        apdex1.combine(apdex2);
        apdex1.calculate();
        assertThat(apdex1.getValue(), is(6500));
    }

    public class ApdexMetricsImpl extends ApdexMetrics {

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
        public int remoteHashCode() {
            return 0;
        }

        @Override
        public void deserialize(RemoteData remoteData) {

        }

        @Override
        public RemoteData.Builder serialize() {
            return null;
        }
    }
}
