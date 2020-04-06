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

package org.apache.skywalking.apm.toolkit.activation.opentracing;

import io.opentracing.Tracer;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMap;
import io.opentracing.tag.Tags;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.apache.skywalking.apm.agent.core.base64.Base64;
import org.apache.skywalking.apm.agent.core.conf.Config;
import org.apache.skywalking.apm.agent.core.conf.Constants;
import org.apache.skywalking.apm.agent.core.context.ContextSnapshot;
import org.apache.skywalking.apm.agent.core.context.SW8CarrierItem;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractTracingSpan;
import org.apache.skywalking.apm.agent.core.context.trace.TraceSegment;
import org.apache.skywalking.apm.agent.core.context.trace.TraceSegmentRef;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.test.helper.SegmentHelper;
import org.apache.skywalking.apm.agent.test.helper.SegmentRefHelper;
import org.apache.skywalking.apm.agent.test.tools.AgentServiceRule;
import org.apache.skywalking.apm.agent.test.tools.SegmentRefAssert;
import org.apache.skywalking.apm.agent.test.tools.SegmentStorage;
import org.apache.skywalking.apm.agent.test.tools.SegmentStoragePoint;
import org.apache.skywalking.apm.agent.test.tools.SpanAssert;
import org.apache.skywalking.apm.agent.test.tools.TracingSegmentRunner;
import org.apache.skywalking.apm.toolkit.activation.opentracing.continuation.ActivateInterceptor;
import org.apache.skywalking.apm.toolkit.activation.opentracing.continuation.ConstructorInterceptor;
import org.apache.skywalking.apm.toolkit.activation.opentracing.span.ConstructorWithSpanBuilderInterceptor;
import org.apache.skywalking.apm.toolkit.activation.opentracing.span.SpanFinishInterceptor;
import org.apache.skywalking.apm.toolkit.activation.opentracing.span.SpanLogInterceptor;
import org.apache.skywalking.apm.toolkit.activation.opentracing.span.SpanSetOperationNameInterceptor;
import org.apache.skywalking.apm.toolkit.activation.opentracing.tracer.SkywalkingTracerExtractInterceptor;
import org.apache.skywalking.apm.toolkit.activation.opentracing.tracer.SkywalkingTracerInjectInterceptor;
import org.apache.skywalking.apm.toolkit.opentracing.SkywalkingContinuation;
import org.apache.skywalking.apm.toolkit.opentracing.SkywalkingSpan;
import org.apache.skywalking.apm.toolkit.opentracing.SkywalkingSpanBuilder;
import org.apache.skywalking.apm.toolkit.opentracing.TextMapContext;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.junit4.PowerMockRunnerDelegate;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(PowerMockRunner.class)
@PowerMockRunnerDelegate(TracingSegmentRunner.class)
public class SkywalkingSpanActivationTest {

    @SegmentStoragePoint
    private SegmentStorage storage;

    @Rule
    public AgentServiceRule serviceRule = new AgentServiceRule();
    private MockEnhancedInstance enhancedInstance = new MockEnhancedInstance();
    private ConstructorWithSpanBuilderInterceptor constructorWithSpanBuilderInterceptor;
    private Tracer.SpanBuilder spanBuilder = new SkywalkingSpanBuilder("test");
    private SpanLogInterceptor spanLogInterceptor;
    private Object[] logArgument;
    private HashMap<String, Object> event = new HashMap<String, Object>() {
        {
            put("a", "A");
        }
    };
    private Class[] logArgumentType;

    private SpanSetOperationNameInterceptor setOperationNameInterceptor;
    private Object[] setOperationNameArgument;
    private Class[] setOperationNameArgumentType;

    private SpanFinishInterceptor spanFinishInterceptor;

    private SkywalkingTracerInjectInterceptor injectInterceptor;

    private SkywalkingTracerExtractInterceptor extractInterceptor;

    private ConstructorInterceptor constructorInterceptor;

    private ActivateInterceptor activateInterceptor;

    @Before
    public void setUp() {
        spanBuilder = new SkywalkingSpanBuilder("test").withTag(Tags.COMPONENT.getKey(), "test");
        constructorWithSpanBuilderInterceptor = new ConstructorWithSpanBuilderInterceptor();
        spanLogInterceptor = new SpanLogInterceptor();
        logArgument = new Object[] {
            111111111L,
            event
        };
        logArgumentType = new Class[] {
            long.class,
            HashMap.class
        };

        Config.Agent.SERVICE_NAME = "service";

        setOperationNameInterceptor = new SpanSetOperationNameInterceptor();
        setOperationNameArgument = new Object[] {"testOperationName"};
        setOperationNameArgumentType = new Class[] {String.class};

        spanFinishInterceptor = new SpanFinishInterceptor();

        injectInterceptor = new SkywalkingTracerInjectInterceptor();
        extractInterceptor = new SkywalkingTracerExtractInterceptor();

        constructorInterceptor = new ConstructorInterceptor();
        activateInterceptor = new ActivateInterceptor();
    }

    @After
    public void tearDown() {
        Config.Agent.SERVICE_NAME = Constants.EMPTY_STRING;
    }

    @Test
    public void testCreateLocalSpan() throws Throwable {
        startSpan();
        stopSpan();

        TraceSegment tracingSegment = assertTraceSemgnets();
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(tracingSegment);
        assertThat(spans.size(), is(1));
        assertThat(spans.get(0).isEntry(), is(false));
        assertThat(spans.get(0).isExit(), is(false));
    }

    @Test
    public void testCreateEntrySpan() throws Throwable {
        spanBuilder.withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_SERVER);
        startSpan();
        stopSpan();

        TraceSegment tracingSegment = assertTraceSemgnets();
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(tracingSegment);
        assertThat(spans.size(), is(1));
        assertSpanCommonsAttribute(spans.get(0));
        assertThat(spans.get(0).isEntry(), is(true));
    }

    @Test
    public void testCreateExitSpanWithoutPeer() throws Throwable {
        spanBuilder.withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT);
        startSpan();
        stopSpan();

        TraceSegment tracingSegment = assertTraceSemgnets();
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(tracingSegment);
        assertThat(spans.size(), is(1));
        assertSpanCommonsAttribute(spans.get(0));
        assertThat(spans.get(0).isEntry(), is(false));
        assertThat(spans.get(0).isExit(), is(false));
    }

    @Test
    public void testCreateExitSpanWithPeer() throws Throwable {
        spanBuilder.withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT)
                   .withTag(Tags.PEER_HOST_IPV4.getKey(), "127.0.0.1")
                   .withTag(Tags.PEER_PORT.getKey(), "8080");
        startSpan();
        stopSpan();

        TraceSegment tracingSegment = assertTraceSemgnets();
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(tracingSegment);
        assertThat(spans.size(), is(1));
        assertSpanCommonsAttribute(spans.get(0));
        assertThat(spans.get(0).isEntry(), is(false));
        assertThat(spans.get(0).isExit(), is(true));
    }

    private TraceSegment assertTraceSemgnets() {
        List<TraceSegment> segments = storage.getTraceSegments();
        assertThat(segments.size(), is(1));

        return segments.get(0);
    }

    @Test
    public void testInject() throws Throwable {
        spanBuilder.withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT)
                   .withTag(Tags.PEER_HOST_IPV4.getKey(), "127.0.0.1")
                   .withTag(Tags.PEER_PORT.getKey(), 8080);
        startSpan();

        final Map<String, String> values = new HashMap<String, String>();
        TextMap carrier = new TextMap() {
            @Override
            public Iterator<Map.Entry<String, String>> iterator() {
                return null;
            }

            @Override
            public void put(String key, String value) {
                values.put(key, value);
            }

        };

        injectInterceptor.afterMethod(enhancedInstance, null, new Object[] {
            new TextMapContext(),
            Format.Builtin.TEXT_MAP,
            carrier
        }, null, null);

        String[] parts = values.get(SW8CarrierItem.HEADER_NAME).split("-", 8);
        Assert.assertEquals("0", parts[3]);
        Assert.assertEquals(Base64.encode("127.0.0.1:8080"), parts[7]);
        stopSpan();
    }

    @Test
    public void testExtractWithValidateContext() throws Throwable {
        spanBuilder.withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT)
                   .withTag(Tags.PEER_HOST_IPV4.getKey(), "127.0.0.1")
                   .withTag(Tags.PEER_PORT.getKey(), 8080);
        startSpan();
        final Map<String, String> values = new HashMap<String, String>();
        TextMap carrier = new TextMap() {
            @Override
            public Iterator<Map.Entry<String, String>> iterator() {
                return values.entrySet().iterator();
            }

            @Override
            public void put(String key, String value) {
                values.put(key, value);
            }

        };

        values.put(
            SW8CarrierItem.HEADER_NAME,
            "1-My40LjU=-MS4yLjM=-3-c2VydmljZQ==-aW5zdGFuY2U=-L2FwcA==-MTI3LjAuMC4xOjgwODA="
        );

        extractInterceptor.afterMethod(enhancedInstance, null, new Object[] {
            Format.Builtin.TEXT_MAP,
            carrier
        }, new Class[] {}, null);
        stopSpan();

        TraceSegment tracingSegment = assertTraceSemgnets();
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(tracingSegment);
        assertThat(tracingSegment.getRefs().size(), is(1));
        TraceSegmentRef ref = tracingSegment.getRefs().get(0);
        SegmentRefAssert.assertSegmentId(ref, "3.4.5");
        SegmentRefAssert.assertSpanId(ref, 3);
        assertThat(SegmentRefHelper.getParentServiceInstance(ref), is("instance"));
        SegmentRefAssert.assertPeerHost(ref, "127.0.0.1:8080");
        assertThat(spans.size(), is(1));
        assertSpanCommonsAttribute(spans.get(0));
    }

    @Test
    public void testExtractWithInValidateContext() throws Throwable {
        spanBuilder.withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT)
                   .withTag(Tags.PEER_HOST_IPV4.getKey(), "127.0.0.1")
                   .withTag(Tags.PEER_PORT.getKey(), 8080);
        startSpan();

        final Map<String, String> values = new HashMap<String, String>();
        TextMap carrier = new TextMap() {
            @Override
            public Iterator<Map.Entry<String, String>> iterator() {
                return values.entrySet().iterator();
            }

            @Override
            public void put(String key, String value) {
                values.put(key, value);
            }

        };

        values.put(SW8CarrierItem.HEADER_NAME, "aaaaaaaa|3|#192.168.1.8:18002|#/portal/|#/testEntrySpan|1.234.444");

        extractInterceptor.afterMethod(enhancedInstance, null, new Object[] {
            Format.Builtin.TEXT_MAP,
            carrier
        }, new Class[] {}, null);
        stopSpan();

        TraceSegment tracingSegment = assertTraceSemgnets();
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(tracingSegment);
        assertNull(tracingSegment.getRefs());
        assertSpanCommonsAttribute(spans.get(0));
    }

    @Test
    public void testContinuation() throws Throwable {
        startSpan();
        final MockEnhancedInstance continuationHolder = new MockEnhancedInstance();
        constructorInterceptor.onConstruct(continuationHolder, null);
        assertTrue(continuationHolder.getSkyWalkingDynamicField() instanceof ContextSnapshot);
        new Thread() {
            @Override
            public void run() {
                MockEnhancedInstance enhancedInstance = new MockEnhancedInstance();
                try {
                    startSpan(enhancedInstance);
                    activateInterceptor.afterMethod(
                        continuationHolder, SkywalkingContinuation.class.getMethod("activate"), null, null, null);
                } catch (Throwable throwable) {
                    throwable.printStackTrace();
                } finally {
                    try {
                        stopSpan(enhancedInstance);
                    } catch (Throwable throwable) {
                        throwable.printStackTrace();
                    }
                }
            }
        }.start();
        Thread.sleep(1000L);
        stopSpan();

        List<TraceSegment> segments = storage.getTraceSegments();
        assertThat(segments.size(), is(2));
        TraceSegment traceSegment = segments.get(0);
        assertThat(traceSegment.getRefs().size(), is(1));

        traceSegment = segments.get(1);
        assertNull(traceSegment.getRefs());
    }

    private void assertSpanCommonsAttribute(AbstractTracingSpan span) {
        assertThat(span.getOperationName(), is("testOperationName"));
        SpanAssert.assertLogSize(span, 1);
    }

    private void stopSpan() throws Throwable {
        stopSpan(enhancedInstance);
    }

    private void stopSpan(EnhancedInstance enhancedInstance) throws Throwable {
        spanFinishInterceptor.afterMethod(enhancedInstance, null, null, null, null);
    }

    private void startSpan() throws Throwable {
        startSpan(enhancedInstance);
    }

    private void startSpan(MockEnhancedInstance enhancedInstance) throws Throwable {
        constructorWithSpanBuilderInterceptor.onConstruct(enhancedInstance, new Object[] {spanBuilder});
        spanLogInterceptor.afterMethod(enhancedInstance, null, logArgument, logArgumentType, null);

        setOperationNameInterceptor.afterMethod(
            enhancedInstance, SkywalkingSpan.class.getMethod("setOperationName", String.class),
            setOperationNameArgument, setOperationNameArgumentType, null
        );
    }

    private class MockEnhancedInstance implements EnhancedInstance {
        public Object object;

        @Override
        public Object getSkyWalkingDynamicField() {
            return object;
        }

        @Override
        public void setSkyWalkingDynamicField(Object value) {
            this.object = value;
        }
    }

    private class MockContinuationThread extends Thread {
        @Override
        public void run() {
            super.run();
        }
    }
}
