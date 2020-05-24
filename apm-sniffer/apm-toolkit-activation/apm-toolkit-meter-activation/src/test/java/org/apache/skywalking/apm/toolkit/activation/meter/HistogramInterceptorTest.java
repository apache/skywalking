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

package org.apache.skywalking.apm.toolkit.activation.meter;

import org.apache.skywalking.apm.agent.core.boot.ServiceManager;
import org.apache.skywalking.apm.agent.core.meter.Histogram;
import org.apache.skywalking.apm.agent.core.meter.MeterId;
import org.apache.skywalking.apm.agent.core.meter.MeterTag;
import org.apache.skywalking.apm.agent.core.meter.MeterType;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.test.tools.AgentServiceRule;
import org.apache.skywalking.apm.agent.test.tools.TracingSegmentRunner;
import org.apache.skywalking.apm.toolkit.meter.Tag;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.internal.util.reflection.Whitebox;

import java.util.Arrays;
import java.util.Collections;

@RunWith(TracingSegmentRunner.class)
public class HistogramInterceptorTest {

    @Rule
    public AgentServiceRule agentServiceRule = new AgentServiceRule();

    private HistogramConstructInterceptor constructInterceptor = new HistogramConstructInterceptor();
    private HistogramAddValueInterceptor addValueInterceptor = new HistogramAddValueInterceptor();
    private HistogramAddCountToStepInterceptor addCountToStepInterceptor = new HistogramAddCountToStepInterceptor();

    private EnhancedInstance enhancedInstance = new EnhancedInstance() {
        private Object data;
        @Override
        public Object getSkyWalkingDynamicField() {
            return data;
        }

        @Override
        public void setSkyWalkingDynamicField(Object value) {
            this.data = value;
        }
    };

    @AfterClass
    public static void afterClass() {
        ServiceManager.INSTANCE.shutdown();
    }

    @Test
    public void testConstruct() {
        constructInterceptor.onConstruct(enhancedInstance, new Object[] {
            "test", Arrays.asList(new Tag("k1", "v1")), Arrays.asList(1, 5, 10)
        });

        final Object field = enhancedInstance.getSkyWalkingDynamicField();
        Assert.assertNotNull(field);
        Assert.assertTrue(field instanceof Histogram);
        final Histogram histogram = (Histogram) field;

        Assert.assertNotNull(histogram.getId());
        Assert.assertEquals(histogram.getId().getName(), "test");
        Assert.assertEquals(histogram.getId().getType(), MeterType.HISTOGRAM);
        Assert.assertEquals(histogram.getId().getTags(), Arrays.asList(new MeterTag("k1", "v1")));

        final Histogram.Bucket[] buckets = (Histogram.Bucket[]) Whitebox.getInternalState(histogram, "buckets");
        Assert.assertNotNull(buckets);
        Assert.assertArrayEquals(buckets, new Histogram.Bucket[] {
            new Histogram.Bucket(1), new Histogram.Bucket(5), new Histogram.Bucket(10)
        });
    }

    @Test
    public void testAddValue() throws Throwable {
        final Histogram histogram = new Histogram(
            new MeterId("test", MeterType.HISTOGRAM, Collections.emptyList()),
            Arrays.asList(1, 5, 10));
        enhancedInstance.setSkyWalkingDynamicField(histogram);

        addValueInterceptor.beforeMethod(enhancedInstance, null, new Object[] {2}, null, null);
        addValueInterceptor.afterMethod(enhancedInstance, null, new Object[] {2}, null, null);

        final Histogram.Bucket[] buckets = (Histogram.Bucket[]) Whitebox.getInternalState(histogram, "buckets");
        Assert.assertEquals(buckets[0].transform().getCount(), 1L);
        Assert.assertEquals(buckets[1].transform().getCount(), 0L);
        Assert.assertEquals(buckets[2].transform().getCount(), 0L);
    }

    @Test
    public void testAddCountToStep() throws Throwable {
        final Histogram histogram = new Histogram(
            new MeterId("test", MeterType.HISTOGRAM, Collections.emptyList()),
            Arrays.asList(1, 5, 10));
        enhancedInstance.setSkyWalkingDynamicField(histogram);

        addCountToStepInterceptor.beforeMethod(enhancedInstance, null, new Object[] {5, 2L}, null, null);
        addCountToStepInterceptor.afterMethod(enhancedInstance, null, new Object[] {5, 2L}, null, null);

        final Histogram.Bucket[] buckets = (Histogram.Bucket[]) Whitebox.getInternalState(histogram, "buckets");
        Assert.assertEquals(buckets[0].transform().getCount(), 0L);
        Assert.assertEquals(buckets[1].transform().getCount(), 2L);
        Assert.assertEquals(buckets[2].transform().getCount(), 0L);
    }


}
