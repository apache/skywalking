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
package org.apache.skywalking.apm.plugin.avro;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.List;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractTracingSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.context.trace.TraceSegment;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.test.helper.SegmentHelper;
import org.apache.skywalking.apm.agent.test.tools.AgentServiceRule;
import org.apache.skywalking.apm.agent.test.tools.SegmentStorage;
import org.apache.skywalking.apm.agent.test.tools.SegmentStoragePoint;
import org.apache.skywalking.apm.agent.test.tools.SpanAssert;
import org.apache.skywalking.apm.agent.test.tools.TracingSegmentRunner;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.junit4.PowerMockRunnerDelegate;

@RunWith(PowerMockRunner.class)
@PowerMockRunnerDelegate(TracingSegmentRunner.class)
public class SpecificRequestorInterceptorTest {

    @SegmentStoragePoint
    private SegmentStorage segmentStorage;

    @Rule
    public AgentServiceRule serviceRule = new AgentServiceRule();

    SpecificRequestorInterceptor interceptor = new SpecificRequestorInterceptor();

    EnhancedInstance instance = new EnhanceInstance();

    Method method;
    Object[] arguments;

    @Before
    public void setup() throws IOException, NoSuchMethodException {
        method = Greeter.class.getMethod("hello", Object.class);
        arguments = new Object[] {
            new Object(),
            method
        };
    }

    @Test
    public void testBefore() throws Throwable {
        interceptor.beforeMethod(instance, null, arguments, null, null);
        interceptor.afterMethod(instance, null, arguments, null, null);

        List<TraceSegment> segments = segmentStorage.getTraceSegments();
        Assert.assertEquals(segments.size(), 1);

        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(segments.get(0));
        Assert.assertEquals(spans.size(), 1);

        AbstractTracingSpan span = spans.get(0);
        spanCommonAssert(span, "example.proto.Greeter.hello");
    }

    private void spanCommonAssert(AbstractSpan span, String operationName) {
        SpanAssert.assertComponent(span, ComponentsDefine.AVRO_CLIENT);
        SpanAssert.assertOccurException(span, false);
        SpanAssert.assertLogSize(span, 0);
        SpanAssert.assertLayer(span, SpanLayer.RPC_FRAMEWORK);

        Assert.assertEquals(span.getOperationName(), operationName);
    }

    @After
    public void cleanup() {

    }

    public static class EnhanceInstance implements EnhancedInstance {

        @Override public Object getSkyWalkingDynamicField() {
            return new AvroInstance("example.proto.Greeter.", "localhost/127.0.0.1:9018");
        }

        @Override public void setSkyWalkingDynamicField(Object value) {

        }
    }

    public interface Greeter {
        String hello(Object message);
    }
}
