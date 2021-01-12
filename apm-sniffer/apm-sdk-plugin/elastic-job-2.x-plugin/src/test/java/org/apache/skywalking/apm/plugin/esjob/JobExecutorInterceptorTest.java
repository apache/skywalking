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

package org.apache.skywalking.apm.plugin.esjob;

import com.dangdang.ddframe.job.executor.ShardingContexts;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractTracingSpan;
import org.apache.skywalking.apm.agent.core.context.trace.TraceSegment;
import org.apache.skywalking.apm.agent.test.helper.SegmentHelper;
import org.apache.skywalking.apm.agent.test.tools.AgentServiceRule;
import org.apache.skywalking.apm.agent.test.tools.SegmentStorage;
import org.apache.skywalking.apm.agent.test.tools.SegmentStoragePoint;
import org.apache.skywalking.apm.network.language.agent.v3.SpanObject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.junit4.PowerMockRunnerDelegate;
import org.apache.skywalking.apm.agent.test.tools.TracingSegmentRunner;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

@RunWith(PowerMockRunner.class)
@PowerMockRunnerDelegate(TracingSegmentRunner.class)
public class JobExecutorInterceptorTest {

    @SegmentStoragePoint
    private SegmentStorage segmentStorage;

    @Rule
    public AgentServiceRule serviceRule = new AgentServiceRule();

    private JobExecutorInterceptor jobExecutorInterceptor;

    @Before
    public void setUp() throws SQLException {
        jobExecutorInterceptor = new JobExecutorInterceptor();
    }

    @Test
    public void assertSuccess() throws Throwable {
        jobExecutorInterceptor.beforeMethod(null, null, new Object[] {
            mockShardingContext("fooJob", 1),
            1
        }, null, null);
        jobExecutorInterceptor.afterMethod(null, null, null, null, null);
        TraceSegment segment = segmentStorage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(segment);
        assertNotNull(spans);
        assertThat(spans.size(), is(1));

        SpanObject.Builder builder = spans.get(0).transform();

        assertThat(builder.getOperationName(), is("ElasticJob/fooJob"));
        assertThat(builder.getComponentId(), is(24));
        assertThat(builder.getTags(0).getKey(), is("x-le"));
        assertThat(builder.getTags(0).getValue(), is("{\"logic-span\":true}"));
        assertThat(builder.getTags(1).getKey(), is("item"));
        assertThat(builder.getTags(1).getValue(), is("1"));
        assertThat(builder.getTags(2).getKey(), is("taskId"));
        assertThat(builder.getTags(2).getValue(), is("fooJob1"));
        assertThat(builder.getTags(3).getKey(), is("shardingTotalCount"));
        assertThat(builder.getTags(3).getValue(), is("2"));
        assertThat(builder.getTags(4).getKey(), is("shardingItemParameters"));
        assertThat(builder.getTags(4).getValue(), is("{1=test}"));
    }

    @Test
    public void assertSuccessWithoutSharding() throws Throwable {
        jobExecutorInterceptor.beforeMethod(null, null, new Object[] {
            mockShardingContext("fooJob", 0),
            0
        }, null, null);
        jobExecutorInterceptor.afterMethod(null, null, null, null, null);
        TraceSegment segment = segmentStorage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(segment);
        assertNotNull(spans);
        assertThat(spans.size(), is(1));

        SpanObject.Builder builder = spans.get(0).transform();

        assertThat(builder.getOperationName(), is("ElasticJob/fooJob"));
        assertThat(builder.getComponentId(), is(24));
        assertThat(builder.getTags(0).getKey(), is("x-le"));
        assertThat(builder.getTags(0).getValue(), is("{\"logic-span\":true}"));
        assertThat(builder.getTags(1).getKey(), is("item"));
        assertThat(builder.getTags(1).getValue(), is("0"));
        assertThat(builder.getTags(2).getKey(), is("taskId"));
        assertThat(builder.getTags(2).getValue(), is("fooJob0"));
        assertThat(builder.getTags(3).getKey(), is("shardingTotalCount"));
        assertThat(builder.getTags(3).getValue(), is("1"));
        assertThat(builder.getTags(4).getKey(), is("shardingItemParameters"));
        assertThat(builder.getTags(4).getValue(), is("{}"));
    }

    @Test
    public void assertError() throws Throwable {
        jobExecutorInterceptor.beforeMethod(null, null, new Object[] {
            mockShardingContext("fooJob", 0),
            0
        }, null, null);
        jobExecutorInterceptor.handleMethodException(null, null, null, null, new Exception("fooError"));
        jobExecutorInterceptor.afterMethod(null, null, null, null, null);
        TraceSegment segment = segmentStorage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(segment);
        assertNotNull(spans);
        assertThat(spans.size(), is(1));
        assertThat(spans.get(0).transform().getIsError(), is(true));
        assertThat(spans.get(0).transform().getLogs(0).getDataList().size(), is(4));
    }

    private ShardingContexts mockShardingContext(String jobName, int shardingItem) {
        Map<Integer, String> shardingMap = new HashMap<Integer, String>(1);
        if (shardingItem >= 1) {
            shardingMap.put(shardingItem, "test");
        }
        return new ShardingContexts(jobName + shardingItem, jobName, shardingItem + 1, "", shardingMap);
    }
}
