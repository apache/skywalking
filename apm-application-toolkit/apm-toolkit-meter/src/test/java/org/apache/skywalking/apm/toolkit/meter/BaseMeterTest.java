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
import org.mockito.Mockito;
import org.mockito.internal.util.reflection.Whitebox;

import java.util.Arrays;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class BaseMeterTest {

    @Test
    public void testBuild() {
        // simple name and tags
        TestMeter.Builder meterBuilder1 = Mockito.spy(TestMeter.create("test_meter"));
        meterBuilder1.tag("k1", "v1");
        final TestMeter testMeter1 = meterBuilder1.build();
        Assert.assertNotNull(testMeter1);

        verify(meterBuilder1, times(1)).create(any());
        verify(meterBuilder1, times(1)).getType();
        verify(meterBuilder1, times(0)).accept(any());

        final MeterId meterId = (MeterId) Whitebox.getInternalState(testMeter1, "meterId");
        Assert.assertNotNull(meterId);
        Assert.assertEquals(meterId.getName(), "test_meter");
        Assert.assertEquals(meterId.getType(), MeterId.MeterType.COUNTER);
        Assert.assertEquals(meterId.getTags(), Arrays.asList(new MeterId.Tag("k1", "v1")));

        // same name and tags
        TestMeter.Builder meterBuilder2 = Mockito.spy(TestMeter.create("test_meter"));
        meterBuilder2.tag("k1", "v1");
        final TestMeter testMeter2 = meterBuilder2.build();
        Assert.assertNotNull(testMeter2);
        verify(meterBuilder2, times(0)).create(any());
        verify(meterBuilder2, times(1)).getType();
        verify(meterBuilder2, times(1)).accept(any());

        // empty name
        try {
            TestMeter.create(null).build();
            throw new IllegalStateException();
        } catch (IllegalStateException e) {
        } catch (Exception e) {
        }
    }

    @Test
    public void testEquals() {
        // same name
        Assert.assertEquals(
            TestMeter.create("test").build(),
            TestMeter.create("test").build()
        );

        // same name and tag
        Assert.assertEquals(
            TestMeter.create("test").tag("k1", "v1").build(),
            TestMeter.create("test").tag("k1", "v1").build()
        );

        // not orders tags
        Assert.assertEquals(
            TestMeter.create("test").tag("k2", "v2").tag("k3", "v3").build(),
            TestMeter.create("test").tag("k3", "v3").tag("k2", "v2").build()
        );

        // not same name
        Assert.assertNotEquals(
            TestMeter.create("test1").build(),
            TestMeter.create("test2").build()
        );

        // same name, not same tag
        Assert.assertNotEquals(
            TestMeter.create("test3").tag("k1", "v1").build(),
            TestMeter.create("test4").tag("k1", "v2").build()
        );
    }

    @Test
    public void testGetName() {
        final TestMeter meter = TestMeter.create("test").build();
        Assert.assertEquals(meter.getName(), "test");
    }

    @Test
    public void testGetTag() {
        final TestMeter meter = TestMeter.create("test").tag("k1", "v1").build();
        Assert.assertEquals(meter.getTag("k1"), "v1");
        Assert.assertNull(meter.getTag("k2"));
    }

    private static class TestMeter extends BaseMeter {
        private TestMeter(MeterId meterId) {
            super(meterId);
        }

        public static TestMeter.Builder create(String name) {
            return new Builder(name);
        }

        public static class Builder extends BaseMeter.Builder<TestMeter> {

            public Builder(String name) {
                super(name);
            }

            @Override
            public TestMeter create(MeterId meterId) {
                return new TestMeter(meterId);
            }

            @Override
            public MeterId.MeterType getType() {
                return MeterId.MeterType.COUNTER;
            }
        }
    }
}
