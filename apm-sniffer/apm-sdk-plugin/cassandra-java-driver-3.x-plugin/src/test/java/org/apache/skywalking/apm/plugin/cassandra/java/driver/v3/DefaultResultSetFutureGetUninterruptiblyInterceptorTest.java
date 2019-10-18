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

import org.apache.skywalking.apm.agent.core.context.trace.AbstractTracingSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.context.trace.TraceSegment;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.test.helper.SegmentHelper;
import org.apache.skywalking.apm.agent.test.tools.*;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.junit4.PowerMockRunnerDelegate;

import java.lang.reflect.Method;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.powermock.api.mockito.PowerMockito.when;

/**
 * @author stone.wlg
 */
@RunWith(PowerMockRunner.class)
@PowerMockRunnerDelegate(TracingSegmentRunner.class)
public class DefaultResultSetFutureGetUninterruptiblyInterceptorTest {

    @Rule
    public AgentServiceRule serviceRule = new AgentServiceRule();
    @SegmentStoragePoint
    private SegmentStorage segmentStorage;

    private DefaultResultSetFutureGetUninterruptiblyInterceptor interceptor;

    @Mock
    private EnhancedInstance objectInstance;
    @Mock
    private Method method;

    @Before
    public void setUp() throws Exception {
        interceptor = new DefaultResultSetFutureGetUninterruptiblyInterceptor();
        when(method.getName()).thenReturn("executeAsync");
    }

    @Test
    public void testCreateLocalSpan() throws Throwable {
        interceptor.beforeMethod(objectInstance, method, null, null, null);
        interceptor.afterMethod(objectInstance, method, null, null, null);

        assertThat(segmentStorage.getTraceSegments().size(), is(1));
        TraceSegment segment = segmentStorage.getTraceSegments().get(0);
        assertThat(SegmentHelper.getSpans(segment).size(), is(1));
        AbstractTracingSpan span = SegmentHelper.getSpans(segment).get(0);
        SpanAssert.assertLayer(span, SpanLayer.DB);
        assertThat(span.getOperationName(), is(Constants.CASSANDRA_OP_PREFIX));
        SpanAssert.assertTag(span, 0, Constants.CASSANDRA_DB_TYPE);
    }
}