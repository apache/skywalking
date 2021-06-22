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

package org.apache.skywalking.apm.plugin.neo4j.v4x;

import static org.apache.skywalking.apm.network.trace.component.ComponentsDefine.NEO4J;
import static org.apache.skywalking.apm.plugin.neo4j.v4x.Neo4jPluginConstants.DB_TYPE;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.powermock.api.mockito.PowerMockito.when;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.MockContextSnapshot;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;
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
import org.apache.skywalking.apm.plugin.neo4j.v4x.Neo4jPluginConfig.Plugin.Neo4j;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.neo4j.driver.Query;
import org.neo4j.driver.Value;
import org.neo4j.driver.internal.BoltServerAddress;
import org.neo4j.driver.internal.DatabaseName;
import org.neo4j.driver.internal.spi.Connection;
import org.neo4j.driver.internal.value.MapValue;
import org.neo4j.driver.internal.value.StringValue;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.junit4.PowerMockRunnerDelegate;

@RunWith(PowerMockRunner.class)
@PowerMockRunnerDelegate(TracingSegmentRunner.class)
@SuppressWarnings("unchecked")
public class SessionRunInterceptorTest {

    private final static String CYPHER = "Match (m:Movie)-[a:ACTED_IN]-(p:Person) RETURN m,a,p";
    private final static String PARAMETERS_STR = "{name: \"John\"}";
    private final static int MAX_LENGTH = 5;
    private final static String PARAMETERS_STR_TOO_LONG = "{name...";
    private final static String BODY_TOO_LONG = "Match...";
    private final Method method = MockMethod.class.getMethod("runAsync");
    @Rule
    public AgentServiceRule serviceRule = new AgentServiceRule();
    @SegmentStoragePoint
    private SegmentStorage segmentStorage;
    private SessionRunInterceptor sessionRunInterceptor;
    private EnhancedInstance enhancedInstance;
    @Mock
    private Query query;
    @Mock
    private Connection connection;
    @Mock
    private DatabaseName databaseName;
    @Mock
    private BoltServerAddress boltServerAddress;

    public SessionRunInterceptorTest() throws NoSuchMethodException {
    }

    @Before
    public void setUp() throws Exception {
        sessionRunInterceptor = new SessionRunInterceptor();
        when(query.text()).thenReturn(CYPHER);
        when(connection.databaseName()).thenReturn(databaseName);
        when(connection.serverAddress()).thenReturn(boltServerAddress);
        when(databaseName.databaseName()).thenReturn(Optional.of("neo4j"));
        when(boltServerAddress.toString()).thenReturn("127.0.0.1:7687");
        Map<String, Value> valueMap = new HashMap<>();
        valueMap.put("name", new StringValue("John"));
        when(query.parameters()).thenReturn(new MapValue(valueMap));
        enhancedInstance = new EnhancedInstance() {
            private Object value;

            @Override
            public Object getSkyWalkingDynamicField() {
                return value;
            }

            @Override
            public void setSkyWalkingDynamicField(Object value) {
                this.value = value;
            }
        };
        SessionRequiredInfo requiredInfo = new SessionRequiredInfo();
        requiredInfo.setContextSnapshot(MockContextSnapshot.INSTANCE.mockContextSnapshot());
        enhancedInstance.setSkyWalkingDynamicField(requiredInfo);
        final AbstractSpan span = ContextManager.createExitSpan("Neo4j", connection.serverAddress().toString());
        Tags.DB_TYPE.set(span, DB_TYPE);
        Tags.DB_INSTANCE.set(span, connection.databaseName().databaseName().orElse(Neo4jPluginConstants.EMPTY_STRING));
        span.setComponent(NEO4J);
        SpanLayer.asDB(span);
        ContextManager.continued(requiredInfo.getContextSnapshot());
        span.prepareForAsync();
        ContextManager.stopSpan();
        requiredInfo.setSpan(span);
        Neo4j.TRACE_CYPHER_PARAMETERS = false;
    }

    @Test
    public void testWithNoConnectionInfo() throws Throwable {
        enhancedInstance.setSkyWalkingDynamicField(null);
        sessionRunInterceptor
                .beforeMethod(enhancedInstance, method, new Object[]{query}, new Class[]{Query.class}, null);
        final CompletionStage<String> result = (CompletionStage<String>) sessionRunInterceptor
                .afterMethod(enhancedInstance, method, new Object[]{query}, new Class[]{Query.class},
                        CompletableFuture.completedFuture("result"));
        assertThat(result.toCompletableFuture().get(), is("result"));
        List<TraceSegment> traceSegments = segmentStorage.getTraceSegments();
        assertThat(traceSegments.size(), is(0));
    }

    @Test
    public void testWithConnectionInfo() throws Throwable {
        doInvokeInterceptorAndAssert();
    }

    @Test
    public void testTraceCypherParameters() throws Throwable {
        Neo4j.TRACE_CYPHER_PARAMETERS = true;
        doInvokeInterceptorAndAssert();
    }

    @Test
    public void testTraceCypherMaxSize() throws Throwable {
        Neo4j.TRACE_CYPHER_PARAMETERS = true;
        Neo4j.CYPHER_PARAMETERS_MAX_LENGTH = MAX_LENGTH;
        Neo4j.CYPHER_BODY_MAX_LENGTH = MAX_LENGTH;
        doInvokeInterceptorAndAssert();
    }

    private void doInvokeInterceptorAndAssert() throws Throwable {
        final CompletionStage<String> result = (CompletionStage<String>) sessionRunInterceptor
                .afterMethod(enhancedInstance, method, new Object[]{query}, new Class[]{Query.class},
                        CompletableFuture.completedFuture("result"));

        assertThat(result.toCompletableFuture().get(), is("result"));
        final List<TraceSegment> traceSegments = segmentStorage.getTraceSegments();
        assertThat(traceSegments.size(), is(1));
        final List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegments.get(0));
        assertNotNull(spans);
        assertThat(spans.size(), is(1));
        assertSpan(spans.get(0));
    }

    private void assertSpan(final AbstractTracingSpan span) {
        SpanAssert.assertLayer(span, SpanLayer.DB);
        SpanAssert.assertComponent(span, ComponentsDefine.NEO4J);
        if (Neo4j.TRACE_CYPHER_PARAMETERS) {
            SpanAssert.assertTagSize(span, 4);
            if (PARAMETERS_STR.length() > Neo4j.CYPHER_PARAMETERS_MAX_LENGTH) {
                SpanAssert.assertTag(span, 3, PARAMETERS_STR_TOO_LONG);
            } else {
                SpanAssert.assertTag(span, 3, PARAMETERS_STR);
            }
        } else {
            SpanAssert.assertTagSize(span, 3);
        }
        SpanAssert.assertTag(span, 0, DB_TYPE);
        SpanAssert.assertTag(span, 1, "neo4j");
        if (CYPHER.length() > Neo4j.CYPHER_BODY_MAX_LENGTH) {
            SpanAssert.assertTag(span, 2, BODY_TOO_LONG);
        } else {
            SpanAssert.assertTag(span, 2, CYPHER);
        }
        assertTrue(span.isExit());
        assertThat(span.getOperationName(), is("Neo4j/Session/runAsync"));
    }
}