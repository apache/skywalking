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

package org.apache.skywalking.apm.plugin.cassandra.java.driver.v3;

import com.datastax.driver.core.SimpleStatement;
import java.lang.reflect.Method;
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
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.junit4.PowerMockRunnerDelegate;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.powermock.api.mockito.PowerMockito.when;

@RunWith(PowerMockRunner.class)
@PowerMockRunnerDelegate(TracingSegmentRunner.class)
public class SessionManagerExecuteAndExecuteAsyncWithStatementArgInterceptorTest {

    @Rule
    public AgentServiceRule serviceRule = new AgentServiceRule();
    @SegmentStoragePoint
    private SegmentStorage segmentStorage;

    private SessionManagerExecuteAndExecuteAsyncWithStatementArgInterceptor interceptor;

    @Mock
    private ConnectionInfo connectionInfo;
    @Mock
    private EnhancedInstance objectInstance;
    @Mock
    private Method method;

    @Before
    public void setUp() throws Exception {
        interceptor = new SessionManagerExecuteAndExecuteAsyncWithStatementArgInterceptor();

        when(objectInstance.getSkyWalkingDynamicField()).thenReturn(connectionInfo);
        when(method.getName()).thenReturn("executeAsync");
        when(connectionInfo.getContactPoints()).thenReturn("localhost:9042");
        when(connectionInfo.getKeyspace()).thenReturn("test");
    }

    @Test
    public void testCreateExitSpan() throws Throwable {
        interceptor.beforeMethod(objectInstance, method, new Object[] {new SimpleStatement("SELECT * FROM test")}, null, null);
        interceptor.afterMethod(objectInstance, method, new Object[] {new SimpleStatement("SELECT * FROM test")}, null, null);

        assertThat(segmentStorage.getTraceSegments().size(), is(1));
        TraceSegment segment = segmentStorage.getTraceSegments().get(0);
        assertThat(SegmentHelper.getSpans(segment).size(), is(1));
        AbstractTracingSpan span = SegmentHelper.getSpans(segment).get(0);
        SpanAssert.assertLayer(span, SpanLayer.DB);
        assertThat(span.getOperationName(), is(Constants.CASSANDRA_OP_PREFIX));
        SpanAssert.assertTag(span, 0, Constants.CASSANDRA_DB_TYPE);
        SpanAssert.assertTag(span, 1, "test");
        SpanAssert.assertTag(span, 2, "SELECT * FROM test");
    }
}