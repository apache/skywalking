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

package org.apache.skywalking.apm.toolkit.meter;

import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;

public class GaugeTest {

    @Test
    public void testBuild() {
        Gauge gauge = MeterFactory.gauge("test_gauge1", () -> 1d).tag("k1", "v1").build();
        Assert.assertNotNull(gauge);

        // Same meter name and new getter
        try {
            MeterFactory.gauge("test_gauge1", () -> 1d).tag("k1", "v1").build();
            throw new IllegalStateException();
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
        }

        // Missing getter reference
        try {
            MeterFactory.gauge("test_gauge2", null).build();
            throw new IllegalStateException();
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
        }

        // Build by meterId
        final Gauge gauge1 = MeterFactory.gauge(new MeterId("test_gauge3", MeterId.MeterType.GAUGE, Collections.emptyList()), () -> 1d).build();
        Assert.assertNotNull(gauge1);
    }

    @Test
    public void testGet() {
        Gauge gauge = MeterFactory.gauge("test_gauge3", () -> 1d).tag("k1", "v1").build();
        Assert.assertEquals(gauge.get(), 1d, 0.0);

        // Need throw exception
        gauge = MeterFactory.gauge("test_gauge4", () -> Double.valueOf(1 / 0)).build();
        try {
            gauge.get();
            throw new IllegalStateException();
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
        }
    }

}
