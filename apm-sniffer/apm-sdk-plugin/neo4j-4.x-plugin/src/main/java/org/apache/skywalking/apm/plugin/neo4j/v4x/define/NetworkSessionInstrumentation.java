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

package org.apache.skywalking.apm.plugin.neo4j.v4x.define;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static org.apache.skywalking.apm.agent.core.plugin.bytebuddy.ArgumentTypeNameMatch.takesArgumentWithType;
import static org.apache.skywalking.apm.agent.core.plugin.match.NameMatch.byName;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.ConstructorInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.InstanceMethodsInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.ClassInstanceMethodsEnhancePluginDefine;
import org.apache.skywalking.apm.agent.core.plugin.match.ClassMatch;

/**
 * This instrumentation enhanced {@link org.neo4j.driver.internal.async.NetworkSession} class used at auto-commit
 * transactions scenario.
 * <p>
 * {@link org.neo4j.driver.internal.async.NetworkSession} class is used by {@link org.neo4j.driver.internal.InternalSession},{@link
 * org.neo4j.driver.internal.async.InternalAsyncSession},
 * and {@link org.neo4j.driver.internal.reactive.InternalRxSession} to send db query and enhance `runAsync()` and
 * `runRx()` method should be more generally.
 * </p>
 * <p>
 * `acquireConnection()` method is used for getting connection information.
 * </p>
 */
public class NetworkSessionInstrumentation extends ClassInstanceMethodsEnhancePluginDefine {

    private final static String ENHANCED_CLASS = "org.neo4j.driver.internal.async.NetworkSession";
    private final static String RUN_ASYNC_METHOD_NAME = "runAsync";
    private final static String RUN_METHOD_INTERCEPTOR = "org.apache.skywalking.apm.plugin.neo4j.v4x.SessionRunInterceptor";
    private final static String RUN_RX_METHOD_NAME = "runRx";
    private final static String ACQUIRE_CONNECTION_METHOD_NAME = "acquireConnection";
    private final static String ACQUIRE_CONNECTION_METHOD_INTERCEPTOR = "org.apache.skywalking.apm.plugin.neo4j.v4x.SessionAcquireConnectionInterceptor";
    private final static String CONSTRUCTOR_INTERCEPTOR = "org.apache.skywalking.apm.plugin.neo4j.v4x.SessionConstructorInterceptor";
    private final static String CONSTRUCTOR_ARGUMENT_TYPE = "org.neo4j.driver.internal.DatabaseName";
    private final static String BEGIN_TRANSACTION_METHOD_NAME = "beginTransactionAsync";
    private final static String BEGIN_TRANSACTION_ARGUMENT_TYPE = "org.neo4j.driver.AccessMode";
    private final static String BEGIN_TRANSACTION_INTERCEPTOR = "org.apache.skywalking.apm.plugin.neo4j.v4x.SessionBeginTransactionInterceptor";

    @Override
    protected ClassMatch enhanceClass() {
        return byName(ENHANCED_CLASS);
    }

    @Override
    public ConstructorInterceptPoint[] getConstructorsInterceptPoints() {
        return new ConstructorInterceptPoint[]{
                new ConstructorInterceptPoint() {
                    @Override
                    public ElementMatcher<MethodDescription> getConstructorMatcher() {
                        return takesArgumentWithType(2, CONSTRUCTOR_ARGUMENT_TYPE);
                    }

                    @Override
                    public String getConstructorInterceptor() {
                        return CONSTRUCTOR_INTERCEPTOR;
                    }
                }
        };
    }

    @Override
    public InstanceMethodsInterceptPoint[] getInstanceMethodsInterceptPoints() {
        return new InstanceMethodsInterceptPoint[]{
                new InstanceMethodsInterceptPoint() {
                    @Override
                    public ElementMatcher<MethodDescription> getMethodsMatcher() {
                        return named(RUN_ASYNC_METHOD_NAME).or(named(RUN_RX_METHOD_NAME));
                    }

                    @Override
                    public String getMethodsInterceptor() {
                        return RUN_METHOD_INTERCEPTOR;
                    }

                    @Override
                    public boolean isOverrideArgs() {
                        return false;
                    }
                },
                new InstanceMethodsInterceptPoint() {
                    @Override
                    public ElementMatcher<MethodDescription> getMethodsMatcher() {
                        return named(ACQUIRE_CONNECTION_METHOD_NAME);
                    }

                    @Override
                    public String getMethodsInterceptor() {
                        return ACQUIRE_CONNECTION_METHOD_INTERCEPTOR;
                    }

                    @Override
                    public boolean isOverrideArgs() {
                        return false;
                    }
                },
                new InstanceMethodsInterceptPoint() {
                    @Override
                    public ElementMatcher<MethodDescription> getMethodsMatcher() {
                        return named(BEGIN_TRANSACTION_METHOD_NAME)
                                .and(takesArgumentWithType(0, BEGIN_TRANSACTION_ARGUMENT_TYPE));
                    }

                    @Override
                    public String getMethodsInterceptor() {
                        return BEGIN_TRANSACTION_INTERCEPTOR;
                    }

                    @Override
                    public boolean isOverrideArgs() {
                        return false;
                    }
                }
        };
    }
}
