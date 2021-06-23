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
import static org.apache.skywalking.apm.agent.core.plugin.match.NameMatch.byName;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.ConstructorInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.InstanceMethodsInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.ClassInstanceMethodsEnhancePluginDefine;
import org.apache.skywalking.apm.agent.core.plugin.match.ClassMatch;

/**
 * This instrumentation enhanced {@link org.neo4j.driver.internal.async.UnmanagedTransaction} class used at Transaction
 * functions scenario.
 * <p>
 * Simple session and async session use {@link org.neo4j.driver.internal.async.UnmanagedTransaction#runAsync} method
 * and
 * reactive session uses {@link org.neo4j.driver.internal.async.UnmanagedTransaction#runRx} method to execute sql
 * statement.
 * </p>
 */
public class UnmanagedTransactionInstrumentation extends ClassInstanceMethodsEnhancePluginDefine {

    private final static String ENHANCED_CLASS = "org.neo4j.driver.internal.async.UnmanagedTransaction";
    private final static String RUN_ASYNC_METHOD_NAME = "runAsync";
    private final static String TRANSACTION_RUN_METHOD_INTERCEPTOR = "org.apache.skywalking.apm.plugin.neo4j.v4x.TransactionRunInterceptor";
    private final static String RUN_RX_METHOD_NAME = "runRx";

    @Override
    protected ClassMatch enhanceClass() {
        return byName(ENHANCED_CLASS);
    }

    @Override
    public ConstructorInterceptPoint[] getConstructorsInterceptPoints() {
        return new ConstructorInterceptPoint[0];
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
                        return TRANSACTION_RUN_METHOD_INTERCEPTOR;
                    }

                    @Override
                    public boolean isOverrideArgs() {
                        return false;
                    }
                }
        };
    }
}
