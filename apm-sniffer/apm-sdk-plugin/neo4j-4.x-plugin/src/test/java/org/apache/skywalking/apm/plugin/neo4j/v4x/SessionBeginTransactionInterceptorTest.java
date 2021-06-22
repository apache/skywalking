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

import static org.junit.Assert.assertNull;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.apache.skywalking.apm.agent.core.context.MockContextSnapshot;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.modules.junit4.PowerMockRunner;

@RunWith(PowerMockRunner.class)
@SuppressWarnings("unchecked")
public class SessionBeginTransactionInterceptorTest {

    private SessionBeginTransactionInterceptor interceptor;
    private EnhancedInstance enhancedInstance;

    @Before
    public void setUp() throws Exception {
        interceptor = new SessionBeginTransactionInterceptor();
        enhancedInstance = new EnhancedInstance() {
            private SessionRequiredInfo sessionRequiredInfo;

            @Override
            public Object getSkyWalkingDynamicField() {
                return sessionRequiredInfo;
            }

            @Override
            public void setSkyWalkingDynamicField(Object value) {
                this.sessionRequiredInfo = (SessionRequiredInfo) value;
            }
        };
    }

    @Test
    public void testWithoutSessionRequiredInfo() throws Throwable {
        final CompletionStage<EnhancedInstance> result = (CompletionStage<EnhancedInstance>) interceptor
                .afterMethod(enhancedInstance, null, null, null, CompletableFuture.completedFuture(
                        new EnhancedInstance() {
                            private Object value;

                            @Override
                            public Object getSkyWalkingDynamicField() {
                                return value;
                            }

                            @Override
                            public void setSkyWalkingDynamicField(Object value) {
                                this.value = value;
                            }
                        }));
        final EnhancedInstance ret = result.toCompletableFuture().get();
        assertNull(ret.getSkyWalkingDynamicField());
    }

    @Test
    public void test() throws Throwable {
        SessionRequiredInfo sessionRequiredInfo = new SessionRequiredInfo();
        sessionRequiredInfo.setContextSnapshot(MockContextSnapshot.INSTANCE.mockContextSnapshot());
        enhancedInstance.setSkyWalkingDynamicField(sessionRequiredInfo);
        CompletionStage<EnhancedInstance> retStage = (CompletionStage<EnhancedInstance>) interceptor
                .afterMethod(enhancedInstance, null, null, null, CompletableFuture.completedFuture(
                        new EnhancedInstance() {
                            private Object value;

                            @Override
                            public Object getSkyWalkingDynamicField() {
                                return value;
                            }

                            @Override
                            public void setSkyWalkingDynamicField(Object value) {
                                this.value = value;
                            }
                        }));
        final EnhancedInstance ret = retStage.toCompletableFuture().get();
        Assert.assertEquals(((SessionRequiredInfo) ret.getSkyWalkingDynamicField()).getContextSnapshot(),
                sessionRequiredInfo.getContextSnapshot());
    }
}