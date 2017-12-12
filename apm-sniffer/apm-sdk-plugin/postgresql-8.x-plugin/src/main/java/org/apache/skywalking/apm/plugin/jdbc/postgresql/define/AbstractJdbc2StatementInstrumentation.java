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


package org.apache.skywalking.apm.plugin.jdbc.postgresql.define;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.InstanceMethodsInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.ClassInstanceMethodsEnhancePluginDefine;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.ConstructorInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.match.ClassMatch;
import org.apache.skywalking.apm.plugin.jdbc.postgresql.StatementExecuteMethodsInterceptor;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;
import static org.apache.skywalking.apm.agent.core.plugin.match.NameMatch.byName;

/**
 * {@link AbstractJdbc2StatementInstrumentation} intercept the following methods that the class which extend {@link
 * org.postgresql.jdbc2.AbstractJdbc2Statement} by {@link StatementExecuteMethodsInterceptor}. <br/>
 * 1. the <code>execute</code> with non parameter
 * 2. the <code>execute</code> with one parameter
 * 3. the <code>executeBatch</code>
 * 4. the <code>executeQuery</code> with non parameter
 * 5. the <code>executeQuery</code> with one parameter
 * 6. the <code>executeUpdate</code> with non parameter
 * 7. the <code>executeUpdate</code> with one parameter
 * 8. the <code>addBatch</code> with non parameter
 * 9. the <code>addBatch</code> with one parameter
 *
 * @author zhangxin
 */
public class AbstractJdbc2StatementInstrumentation extends ClassInstanceMethodsEnhancePluginDefine {

    private static final String ENHANCE_CLASS = "org.postgresql.jdbc2.AbstractJdbc2Statement";
    private static final String INTERCEPTOR_CLASS = "org.apache.skywalking.apm.plugin.jdbc.postgresql.StatementExecuteMethodsInterceptor";

    @Override protected ConstructorInterceptPoint[] getConstructorsInterceptPoints() {
        return new ConstructorInterceptPoint[0];
    }

    @Override protected InstanceMethodsInterceptPoint[] getInstanceMethodsInterceptPoints() {
        return new InstanceMethodsInterceptPoint[] {
            new InstanceMethodsInterceptPoint() {
                @Override public ElementMatcher<MethodDescription> getMethodsMatcher() {
                    return named("execute").and(takesArguments(0))
                        .or(named("execute").and(takesArguments(1)))
                        .or(named("executeBatch"))
                        .or(named("executeQuery").and(takesArguments(0)))
                        .or(named("executeQuery").and(takesArguments(1)))
                        .or(named("executeUpdate").and(takesArguments(0)))
                        .or(named("executeUpdate").and(takesArguments(1)));
                }

                @Override public String getMethodsInterceptor() {
                    return INTERCEPTOR_CLASS;
                }

                @Override public boolean isOverrideArgs() {
                    return false;
                }
            }
        };
    }

    @Override protected ClassMatch enhanceClass() {
        return byName(ENHANCE_CLASS);
    }
}
