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

package org.apache.skywalking.apm.plugin.sjdbc;

import com.dangdang.ddframe.rdb.sharding.constant.SQLType;
import com.dangdang.ddframe.rdb.sharding.executor.event.DMLExecutionEvent;
import com.dangdang.ddframe.rdb.sharding.executor.event.DQLExecutionEvent;
import com.dangdang.ddframe.rdb.sharding.executor.event.EventExecutionType;
import com.dangdang.ddframe.rdb.sharding.executor.threadlocal.ExecutorDataMap;
import com.dangdang.ddframe.rdb.sharding.util.EventBusInstance;
import com.google.common.base.Optional;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.test.helper.SegmentHelper;
import org.apache.skywalking.apm.agent.test.tools.SegmentStorage;
import org.apache.skywalking.apm.agent.test.tools.SegmentStoragePoint;
import org.apache.skywalking.apm.agent.test.tools.SpanAssert;
import org.apache.skywalking.apm.plugin.sjdbc.define.AsyncExecuteInterceptor;
import org.apache.skywalking.apm.plugin.sjdbc.define.ExecutorEngineConstructorInterceptor;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.junit4.PowerMockRunnerDelegate;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractTracingSpan;
import org.apache.skywalking.apm.agent.core.context.trace.TraceSegment;
import org.apache.skywalking.apm.agent.test.tools.AgentServiceRule;
import org.apache.skywalking.apm.agent.test.tools.TracingSegmentRunner;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;
import org.apache.skywalking.apm.plugin.sjdbc.define.ExecuteInterceptor;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.apache.skywalking.apm.agent.test.tools.SpanAssert.assertComponent;

@RunWith(PowerMockRunner.class)
@PowerMockRunnerDelegate(TracingSegmentRunner.class)
public class InterceptorTest {

    private static ExecutorService ES;

    @SegmentStoragePoint
    private SegmentStorage segmentStorage;

    @Rule
    public AgentServiceRule serviceRule = new AgentServiceRule();

    private ExecuteInterceptor executeInterceptor;

    private AsyncExecuteInterceptor asyncExecuteInterceptor;

    private Object[] allArguments;

    @BeforeClass
    public static void init() {
        ExecuteEventListener.init();
        new ExecutorEngineConstructorInterceptor().onConstruct(null, null);
        ES = Executors.newSingleThreadExecutor();
    }

    @AfterClass
    public static void finish() {
        ES.shutdown();
    }

    @Before
    public void setUp() throws SQLException {
        executeInterceptor = new ExecuteInterceptor();
        asyncExecuteInterceptor = new AsyncExecuteInterceptor();
        allArguments = new Object[] {
            SQLType.DQL,
            null
        };
    }

    @Test
    public void assertSyncExecute() throws Throwable {
        executeInterceptor.beforeMethod(null, null, allArguments, null, null);
        sendEvent("ds_0", "select * from t_order_0");
        executeInterceptor.afterMethod(null, null, allArguments, null, null);
        assertThat(segmentStorage.getTraceSegments().size(), is(1));
        TraceSegment segment = segmentStorage.getTraceSegments().get(0);
        List<AbstractTracingSpan> spans = SegmentHelper.getSpans(segment);
        assertNotNull(spans);
        assertThat(spans.size(), is(2));
        assertSpan(spans.get(0), 0);
        assertThat(spans.get(1).getOperationName(), is("/SJDBC/TRUNK/DQL"));
    }

    @Test
    public void assertAsyncExecute() throws Throwable {
        executeInterceptor.beforeMethod(null, null, allArguments, null, null);
        asyncExecuteInterceptor.beforeMethod(null, null, null, null, null);
        final Map<String, Object> dataMap = ExecutorDataMap.getDataMap();
        ES.submit(new Runnable() {
            @Override
            public void run() {
                ExecutorDataMap.setDataMap(dataMap);
                sendEvent("ds_1", "select * from t_order_1");
            }
        }).get();
        asyncExecuteInterceptor.afterMethod(null, null, null, null, null);
        sendEvent("ds_0", "select * from t_order_0");
        executeInterceptor.afterMethod(null, null, allArguments, null, null);
        assertThat(segmentStorage.getTraceSegments().size(), is(2));
        TraceSegment segment0 = segmentStorage.getTraceSegments().get(0);
        TraceSegment segment1 = segmentStorage.getTraceSegments().get(1);
        assertThat(segment0.getRefs().size(), is(1));
        assertNull(segment1.getRefs());
        List<AbstractTracingSpan> spans0 = SegmentHelper.getSpans(segment0);
        assertNotNull(spans0);
        assertThat(spans0.size(), is(1));
        assertSpan(spans0.get(0), 1);
        List<AbstractTracingSpan> spans1 = SegmentHelper.getSpans(segment1);
        assertNotNull(spans1);
        assertThat(spans1.size(), is(2));
        assertSpan(spans1.get(0), 0);
        assertThat(spans1.get(1).getOperationName(), is("/SJDBC/TRUNK/DQL"));
    }

    @Test
    public void assertExecuteError() throws Throwable {
        executeInterceptor.beforeMethod(null, null, allArguments, null, null);
        asyncExecuteInterceptor.beforeMethod(null, null, null, null, null);
        final Map<String, Object> dataMap = ExecutorDataMap.getDataMap();
        ES.submit(new Runnable() {
            @Override
            public void run() {
                ExecutorDataMap.setDataMap(dataMap);
                sendError();
            }
        }).get();
        asyncExecuteInterceptor.handleMethodException(null, null, null, null, new SQLException("test"));
        asyncExecuteInterceptor.afterMethod(null, null, null, null, null);
        sendEvent("ds_0", "select * from t_order_0");
        executeInterceptor.handleMethodException(null, null, allArguments, null, new SQLException("Test"));
        executeInterceptor.afterMethod(null, null, allArguments, null, null);
        assertThat(segmentStorage.getTraceSegments().size(), is(2));
        TraceSegment segment0 = segmentStorage.getTraceSegments().get(0);
        TraceSegment segment1 = segmentStorage.getTraceSegments().get(1);
        List<AbstractTracingSpan> spans0 = SegmentHelper.getSpans(segment0);
        assertNotNull(spans0);
        assertThat(spans0.size(), is(1));
        assertErrorSpan(spans0.get(0));
        List<AbstractTracingSpan> spans1 = SegmentHelper.getSpans(segment1);
        assertNotNull(spans1);
        assertThat(spans1.size(), is(2));
        assertSpan(spans1.get(0), 0);
        assertErrorSpan(spans1.get(1));
    }

    private void assertSpan(AbstractTracingSpan span, int index) {
        assertComponent(span, ComponentsDefine.SHARDING_JDBC);
        SpanAssert.assertLayer(span, SpanLayer.DB);
        SpanAssert.assertTag(span, 0, "sql");
        SpanAssert.assertTag(span, 1, "ds_" + index);
        SpanAssert.assertTag(span, 2, "select * from t_order_" + index);
        assertThat(span.isExit(), is(true));
        assertThat(span.getOperationName(), is("/SJDBC/BRANCH/QUERY"));
    }

    private void assertErrorSpan(AbstractTracingSpan span) {
        SpanAssert.assertOccurException(span, true);
    }

    private void sendEvent(String datasource, String sql) {
        List<Object> parameters = new ArrayList<Object>();
        parameters.add("1");
        parameters.add(100);

        DQLExecutionEvent event = new DQLExecutionEvent(datasource, sql, parameters);
        EventBusInstance.getInstance().post(event);
        event.setEventExecutionType(EventExecutionType.EXECUTE_SUCCESS);
        EventBusInstance.getInstance().post(event);
    }

    private void sendError() {
        DMLExecutionEvent event = new DMLExecutionEvent("", "", Collections.emptyList());
        EventBusInstance.getInstance().post(event);
        event.setEventExecutionType(EventExecutionType.EXECUTE_FAILURE);
        event.setException(Optional.of(new SQLException("Test")));
        EventBusInstance.getInstance().post(event);
    }
}
