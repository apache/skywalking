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

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.powermock.api.mockito.PowerMockito.when;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.apache.skywalking.apm.agent.core.context.MockContextSnapshot;
import org.apache.skywalking.apm.agent.core.context.trace.TraceSegment;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.test.tools.AgentServiceRule;
import org.apache.skywalking.apm.agent.test.tools.SegmentStorage;
import org.apache.skywalking.apm.agent.test.tools.SegmentStoragePoint;
import org.apache.skywalking.apm.agent.test.tools.TracingSegmentRunner;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.neo4j.driver.internal.BoltServerAddress;
import org.neo4j.driver.internal.DatabaseName;
import org.neo4j.driver.internal.spi.Connection;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.junit4.PowerMockRunnerDelegate;

@RunWith(PowerMockRunner.class)
@PowerMockRunnerDelegate(TracingSegmentRunner.class)
public class SessionAcquireConnectionInterceptorTest {

    private final EnhancedInstance enhancedInstance = new EnhancedInstance() {
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
    @Rule
    public AgentServiceRule serviceRule = new AgentServiceRule();
    @SegmentStoragePoint
    private SegmentStorage segmentStorage;
    private SessionAcquireConnectionInterceptor interceptor;
    @Mock
    private Connection connection;
    @Mock
    private DatabaseName databaseName;
    @Mock
    private BoltServerAddress boltServerAddress;

    @Before
    public void setUp() throws Exception {
        when(connection.databaseName()).thenReturn(databaseName);
        when(connection.serverAddress()).thenReturn(boltServerAddress);
        when(databaseName.databaseName()).thenReturn(Optional.of("neo4j"));
        when(boltServerAddress.toString()).thenReturn("127.0.0.1:7687");
        interceptor = new SessionAcquireConnectionInterceptor();
        SessionRequiredInfo requiredInfo = new SessionRequiredInfo();
        requiredInfo.setContextSnapshot(MockContextSnapshot.INSTANCE.mockContextSnapshot());
        enhancedInstance.setSkyWalkingDynamicField(requiredInfo);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void test() throws Throwable {
        interceptor.beforeMethod(enhancedInstance, null, null, null, null);
        final CompletionStage<Connection> stage = (CompletionStage<Connection>) interceptor
                .afterMethod(enhancedInstance, null, null, null, CompletableFuture.completedFuture(connection));
        stage.whenComplete((connection1, throwable) -> {
            SessionRequiredInfo requiredInfo = (SessionRequiredInfo) enhancedInstance
                    .getSkyWalkingDynamicField();
            assertNotNull(requiredInfo);
            assertNotNull(requiredInfo.getContextSnapshot());
            assertNotNull(requiredInfo.getSpan());
            final List<TraceSegment> traceSegments = segmentStorage.getTraceSegments();
            assertNotNull(traceSegments);
            assertThat(traceSegments.size(), is(0));
        });
    }
}