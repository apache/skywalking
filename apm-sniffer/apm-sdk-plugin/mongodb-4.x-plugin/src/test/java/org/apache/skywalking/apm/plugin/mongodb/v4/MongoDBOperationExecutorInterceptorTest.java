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

package org.apache.skywalking.apm.plugin.mongodb.v4;

import com.mongodb.MongoNamespace;
import com.mongodb.ReadConcern;
import com.mongodb.client.internal.OperationExecutor;
import com.mongodb.internal.operation.FindOperation;
import com.mongodb.internal.operation.WriteOperation;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractTracingSpan;
import org.apache.skywalking.apm.agent.core.context.trace.LogDataEntity;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.context.trace.TraceSegment;
import org.apache.skywalking.apm.agent.core.context.util.TagValuePair;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.test.helper.SegmentHelper;
import org.apache.skywalking.apm.agent.test.helper.SpanHelper;
import org.apache.skywalking.apm.agent.test.tools.AgentServiceRule;
import org.apache.skywalking.apm.agent.test.tools.SegmentStorage;
import org.apache.skywalking.apm.agent.test.tools.SegmentStoragePoint;
import org.apache.skywalking.apm.agent.test.tools.SpanAssert;
import org.apache.skywalking.apm.agent.test.tools.TracingSegmentRunner;
import org.apache.skywalking.apm.plugin.mongodb.v4.interceptor.MongoDBOperationExecutorInterceptor;
import org.apache.skywalking.apm.plugin.mongodb.v4.support.MongoPluginConfig;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.bson.codecs.Decoder;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.junit4.PowerMockRunnerDelegate;

import java.lang.reflect.Method;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.when;

@RunWith(PowerMockRunner.class)
@PowerMockRunnerDelegate(TracingSegmentRunner.class)
public class MongoDBOperationExecutorInterceptorTest {

    @SegmentStoragePoint
    private SegmentStorage segmentStorage;

    @Rule
    public AgentServiceRule serviceRule = new AgentServiceRule();

    @Mock
    private EnhancedInstance enhancedInstance;

    private MongoDBOperationExecutorInterceptor interceptor;

    private Object[] arguments;

    private Class[] argumentTypes;

    @Before
    public void setUp() {

        interceptor = new MongoDBOperationExecutorInterceptor();

        MongoPluginConfig.Plugin.MongoDB.TRACE_PARAM = true;

        when(enhancedInstance.getSkyWalkingDynamicField()).thenReturn("127.0.0.1:27017");

        BsonDocument document = new BsonDocument();
        document.append("name", new BsonString("by"));
        MongoNamespace mongoNamespace = new MongoNamespace("test.user");
        Decoder decoder = PowerMockito.mock(Decoder.class);
        FindOperation findOperation = new FindOperation(mongoNamespace, decoder);
        findOperation.filter(document);

        arguments = new Object[] {findOperation};
        argumentTypes = new Class[] {findOperation.getClass()};
    }

    @Test
    public void testIntercept() throws Throwable {
        interceptor.beforeMethod(enhancedInstance, getMethod(), arguments, argumentTypes, null);
        interceptor.afterMethod(enhancedInstance, getMethod(), arguments, argumentTypes, null);

        MatcherAssert.assertThat(segmentStorage.getTraceSegments().size(), is(1));
        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);
        assertMongoSpan(spans.get(0));
    }

    @Test
    public void testInterceptWithException() throws Throwable {
        interceptor.beforeMethod(enhancedInstance, getMethod(), arguments, argumentTypes, null);
        interceptor.handleMethodException(
            enhancedInstance, getMethod(), arguments, argumentTypes, new RuntimeException());
        interceptor.afterMethod(enhancedInstance, getMethod(), arguments, argumentTypes, null);

        MatcherAssert.assertThat(segmentStorage.getTraceSegments().size(), is(1));
        TraceSegment traceSegment = segmentStorage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegment);
        assertMongoSpan(spans.get(0));
        List<LogDataEntity> logDataEntities = SpanHelper.getLogs(spans.get(0));
        assertThat(logDataEntities.size(), is(1));
        SpanAssert.assertException(logDataEntities.get(0), RuntimeException.class);
    }

    private void assertMongoSpan(AbstractTracingSpan span) {
        assertThat(span.getOperationName(), is("MongoDB/FindOperation"));
        assertThat(SpanHelper.getComponentId(span), is(42));
        List<TagValuePair> tags = SpanHelper.getTags(span);
        assertThat(tags.get(1).getValue(), is("{\"name\": \"by\"}"));
        assertThat(tags.get(0).getValue(), is("MongoDB"));
        assertThat(span.isExit(), is(true));
        assertThat(SpanHelper.getLayer(span), CoreMatchers.is(SpanLayer.DB));
    }

    private Method getMethod() throws Exception {
        return OperationExecutor.class.getMethod("execute", WriteOperation.class, ReadConcern.class);
    }
}
