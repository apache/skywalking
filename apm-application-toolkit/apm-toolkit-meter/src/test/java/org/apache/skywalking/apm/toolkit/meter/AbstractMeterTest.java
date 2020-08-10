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

import org.apache.skywalking.apm.toolkit.meter.impl.AbstractBuilder;
import org.apache.skywalking.apm.toolkit.meter.impl.AbstractMeter;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.internal.util.reflection.Whitebox;

import java.util.Arrays;
import java.util.Collections;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class AbstractMeterTest {

    @Test
    public void testBuild() {
        // simple name and tags
        TestCounter.Builder meterBuilder1 = Mockito.spy(TestCounter.create("test_meter"));
        meterBuilder1.tag("k1", "v1");
        final Counter testCounter1 = meterBuilder1.build();
        Assert.assertNotNull(testCounter1);

        verify(meterBuilder1, times(1)).create(any());
        verify(meterBuilder1, times(0)).accept(any());

        final MeterId meterId = (MeterId) Whitebox.getInternalState(testCounter1, "meterId");
        Assert.assertNotNull(meterId);
        Assert.assertEquals(meterId.getName(), "test_meter");
        Assert.assertEquals(meterId.getType(), MeterId.MeterType.COUNTER);
        Assert.assertEquals(meterId.getTags(), Arrays.asList(new MeterId.Tag("k1", "v1")));

        // same name and tags
        TestCounter.Builder meterBuilder2 = Mockito.spy(TestCounter.create("test_meter"));
        meterBuilder2.tag("k1", "v1");
        final Counter testCounter2 = meterBuilder2.build();
        Assert.assertNotNull(testCounter2);
        verify(meterBuilder2, times(0)).create(any());
        verify(meterBuilder2, times(1)).accept(any());

        // empty name
        try {
            TestCounter.create(null).build();
            throw new RuntimeException();
        } catch (IllegalArgumentException e) {
        } catch (Exception e) {
            throw e;
        }

        // correct meter id
        final MeterId tmpMeterId = new MeterId("test_meter_builder", MeterId.MeterType.COUNTER, Collections.emptyList());
        new TestCounter.Builder(tmpMeterId).build();

        final MeterId copiedMeterId = tmpMeterId.copyTo("test_meter_builder", MeterId.MeterType.GAUGE);
        // not matched type
        try {
            new TestCounter.Builder(copiedMeterId).build();
            throw new RuntimeException();
        } catch (IllegalArgumentException e) {
        } catch (Exception e) {
            throw e;
        }

        // empty meterId
        try {
            MeterId emptyId = null;
            new TestCounter.Builder(emptyId).build();
            throw new RuntimeException();
        } catch (IllegalArgumentException e) {
        } catch (Exception e) {
            throw e;
        }
    }

    @Test
    public void testEquals() {
        // same name
        Assert.assertEquals(
            TestCounter.create("test").build(),
            TestCounter.create("test").build()
        );

        // same name and tag
        Assert.assertEquals(
            TestCounter.create("test").tag("k1", "v1").build(),
            TestCounter.create("test").tag("k1", "v1").build()
        );

        // not orders tags
        Assert.assertEquals(
            TestCounter.create("test").tag("k2", "v2").tag("k3", "v3").build(),
            TestCounter.create("test").tag("k3", "v3").tag("k2", "v2").build()
        );

        // not same name
        Assert.assertNotEquals(
            TestCounter.create("test1").build(),
            TestCounter.create("test2").build()
        );

        // same name, not same tag
        Assert.assertNotEquals(
            TestCounter.create("test3").tag("k1", "v1").build(),
            TestCounter.create("test4").tag("k1", "v2").build()
        );
    }

    @Test
    public void testGetName() {
        final Counter meter = TestCounter.create("test").build();
        Assert.assertEquals(meter.getName(), "test");
    }

    @Test
    public void testGetTag() {
        final Counter meter = TestCounter.create("test").tag("k1", "v1").build();
        Assert.assertEquals(meter.getTag("k1"), "v1");
        Assert.assertNull(meter.getTag("k2"));
    }

    @Test
    public void testGetMeterId() {
        final Counter meter = TestCounter.create("test").tag("k1", "v1").build();
        final MeterId tmpMeterId = new MeterId("test", MeterId.MeterType.COUNTER,
            Arrays.asList(new MeterId.Tag("k1", "v1")));
        final TestCounter counter = (TestCounter) meter;
        Assert.assertEquals(tmpMeterId, counter.getMeterId());
    }

    private static class TestCounter extends AbstractMeter implements Counter {
        private TestCounter(MeterId meterId) {
            super(meterId);
        }

        public static TestCounter.Builder create(String name) {
            return new Builder(name);
        }

        @Override
        public void increment(double count) {
        }

        @Override
        public double get() {
            return 0;
        }

        public static class Builder extends AbstractBuilder<Builder, Counter, TestCounter> {

            public Builder(String name) {
                super(name);
            }

            public Builder(MeterId meterId) {
                super(meterId);
            }

            @Override
            public TestCounter create(MeterId meterId) {
                return new TestCounter(meterId);
            }

            @Override
            protected void accept(TestCounter meter) throws IllegalArgumentException {
            }

            @Override
            public MeterId.MeterType getType() {
                return MeterId.MeterType.COUNTER;
            }
        }
    }
}
