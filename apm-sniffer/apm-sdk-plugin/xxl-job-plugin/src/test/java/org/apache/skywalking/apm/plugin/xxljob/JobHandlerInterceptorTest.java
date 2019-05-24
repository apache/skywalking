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

package org.apache.skywalking.apm.plugin.xxljob;

import org.apache.skywalking.apm.agent.core.context.trace.AbstractTracingSpan;
import org.apache.skywalking.apm.agent.core.context.trace.TraceSegment;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.test.helper.SegmentHelper;
import org.apache.skywalking.apm.agent.test.tools.AgentServiceRule;
import org.apache.skywalking.apm.agent.test.tools.SegmentStorage;
import org.apache.skywalking.apm.agent.test.tools.SegmentStoragePoint;
import org.apache.skywalking.apm.agent.test.tools.TracingSegmentRunner;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.junit4.PowerMockRunnerDelegate;

import java.sql.SQLException;
import java.util.List;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

/**
 * @author tianjunwei
 * @date 2019/4/26 14:06
 */

@RunWith(PowerMockRunner.class)
@PowerMockRunnerDelegate(TracingSegmentRunner.class)
public class JobHandlerInterceptorTest {

    @SegmentStoragePoint
    private SegmentStorage segmentStorage;

    @Rule
    public AgentServiceRule serviceRule = new AgentServiceRule();

    private JobHandlerInterceptor jobHandlerInterceptor;

    private EnhancedInstance enhancedInstance;


    @Before
    public void setUp() throws SQLException {
        jobHandlerInterceptor = new JobHandlerInterceptor();

        enhancedInstance = new EnhancedInstance() {

            private EnhanceRequireObjectCache cache;

            @Override
            public Object getSkyWalkingDynamicField() {
                return cache;
            }

            @Override public void setSkyWalkingDynamicField(Object cache) {
                this.cache = (EnhanceRequireObjectCache) cache;
            }
        };

    }

    @Test
    public void assertSuccess() throws Throwable {
        jobHandlerInterceptor.beforeMethod(enhancedInstance, null, new Object[]{mockShardingContext("xxljob"), 1}, null, null);
        jobHandlerInterceptor.afterMethod(enhancedInstance, null, null, null, null);
        TraceSegment segment = segmentStorage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(segment);
        assertNotNull(spans);
        assertThat(spans.size(), is(1));
        assertThat(spans.get(0).transform().getOperationName(), is(ComponentsDefine.XXL_JOB.getName()));
        assertThat(spans.get(0).transform().getComponentId(), is(ComponentsDefine.XXL_JOB.getId()));
        assertThat(spans.get(0).transform().getTags(0).getKey(), is("triggerParam"));
        assertThat(spans.get(0).transform().getTags(0).getValue(), is("xxljob"));
    }

    @Test
    public void assertSuccessWithoutSharding() throws Throwable {
        jobHandlerInterceptor.beforeMethod(enhancedInstance, null, new Object[]{mockShardingContext("xxljob"), 0}, null, null);
        jobHandlerInterceptor.afterMethod(enhancedInstance, null, null, null, null);
        TraceSegment segment = segmentStorage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(segment);
        assertNotNull(spans);
        assertThat(spans.size(), is(1));
        assertThat(spans.get(0).transform().getOperationName(), is(ComponentsDefine.XXL_JOB.getName()));
        assertThat(spans.get(0).transform().getTags(0).getValue(), is("xxljob"));
    }

    @Test
    public void assertError() throws Throwable {
        jobHandlerInterceptor.beforeMethod(enhancedInstance, null, new Object[]{mockShardingContext("xxljob"), 0}, null, null);
        jobHandlerInterceptor.handleMethodException(enhancedInstance, null, null, null, new Exception("fooError"));
        jobHandlerInterceptor.afterMethod(enhancedInstance, null, null, null, null);
        TraceSegment segment = segmentStorage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(segment);
        assertNotNull(spans);
        assertThat(spans.size(), is(1));
        assertThat(spans.get(0).transform().getIsError(), is(true));
        assertThat(spans.get(0).transform().getLogs(0).getDataList().size(), is(4));
    }

    private String mockShardingContext(String param) {
        return param;
    }

}
