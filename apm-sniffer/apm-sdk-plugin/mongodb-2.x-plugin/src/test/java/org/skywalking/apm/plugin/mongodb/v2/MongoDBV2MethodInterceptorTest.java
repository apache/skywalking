/*
 * Copyright 2017, OpenSkywalking Organization All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Project repository: https://github.com/OpenSkywalking/skywalking
 */

package org.skywalking.apm.plugin.mongodb.v2;

import com.mongodb.Mongo;
import java.lang.reflect.Method;
import java.util.List;
import org.hamcrest.MatcherAssert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.junit4.PowerMockRunnerDelegate;
import org.skywalking.apm.agent.core.conf.Config;
import org.skywalking.apm.agent.core.context.trace.AbstractTracingSpan;
import org.skywalking.apm.agent.core.context.trace.LogDataEntity;
import org.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.skywalking.apm.agent.core.context.trace.TraceSegment;
import org.skywalking.apm.agent.core.context.util.KeyValuePair;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.skywalking.apm.agent.test.helper.SegmentHelper;
import org.skywalking.apm.agent.test.helper.SpanHelper;
import org.skywalking.apm.agent.test.tools.AgentServiceRule;
import org.skywalking.apm.agent.test.tools.SegmentStorage;
import org.skywalking.apm.agent.test.tools.SegmentStoragePoint;
import org.skywalking.apm.agent.test.tools.TracingSegmentRunner;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.when;
import static org.skywalking.apm.agent.test.tools.SpanAssert.assertException;

@RunWith(PowerMockRunner.class)
@PowerMockRunnerDelegate(TracingSegmentRunner.class)
public class MongoDBV2MethodInterceptorTest {

    @SegmentStoragePoint
    private SegmentStorage segmentStorage;

    @Rule
    public AgentServiceRule serviceRule = new AgentServiceRule();

    private MongoDBV2MethodInterceptor interceptor;

    @Mock
    private EnhancedInstance enhancedInstance;

    private Object[] arguments;
    private Class[] argumentTypes;

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Before
    public void setUp() throws Exception {

        interceptor = new MongoDBV2MethodInterceptor();

        Config.Plugin.MongoDB.TRACE_PARAM = true;

        when(enhancedInstance.getSkyWalkingDynamicField()).thenReturn("127.0.0.1:27017");

    }

    @Test
    public void testIntercept() throws Throwable {
        interceptor.beforeMethod(enhancedInstance, getExecuteMethod(), null, null, null);
        interceptor.afterMethod(enhancedInstance, getExecuteMethod(), null, null, null);

        MatcherAssert.assertThat(segmentStorage.getTraceSegments().size(), is(1));
        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);
        assertMongoSpan(spans.get(0));
    }

    @Test
    public void testInterceptWithException() throws Throwable {
        interceptor.beforeMethod(enhancedInstance, getExecuteMethod(), null, null, null);
        interceptor.handleMethodException(enhancedInstance, getExecuteMethod(), null, null, new RuntimeException());
        interceptor.afterMethod(enhancedInstance, getExecuteMethod(), null, null, null);

        MatcherAssert.assertThat(segmentStorage.getTraceSegments().size(), is(1));
        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);
        assertMongoSpan(spans.get(0));
        List<LogDataEntity> logDataEntities = SpanHelper.getLogs(spans.get(0));
        assertThat(logDataEntities.size(), is(1));
        assertException(logDataEntities.get(0), RuntimeException.class);
    }

    private void assertMongoSpan(AbstractTracingSpan span) {
        assertThat(span.getOperationName(), is("MongoDB/getWriteConcern"));
        assertThat(SpanHelper.getComponentId(span), is(9));
        List<KeyValuePair> tags = SpanHelper.getTags(span);
        assertThat(tags.get(0).getValue(), is("MongoDB"));
        assertThat(span.isExit(), is(true));
        assertThat(SpanHelper.getLayer(span), is(SpanLayer.DB));
    }

    private Method getExecuteMethod() {
        try {
            return Mongo.class.getMethod("getWriteConcern");
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

}
