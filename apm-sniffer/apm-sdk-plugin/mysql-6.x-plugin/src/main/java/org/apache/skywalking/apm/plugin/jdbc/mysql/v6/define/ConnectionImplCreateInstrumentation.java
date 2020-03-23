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

package org.apache.skywalking.apm.plugin.jdbc.mysql.v6.define;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.StaticMethodsInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.match.ClassMatch;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;
import static org.apache.skywalking.apm.agent.core.plugin.match.NameMatch.byName;

/**
 * interceptor the method {@link com.mysql.cj.jdbc.ConnectionImpl#getInstance} for mysql client version 6.x
 */
public class ConnectionImplCreateInstrumentation extends AbstractMysqlInstrumentation {

    private static final String JDBC_ENHANCE_CLASS = "com.mysql.cj.jdbc.ConnectionImpl";

    private static final String CONNECT_METHOD = "getInstance";

    private static final String GET_INSTANCE_NEW_INTERCEPTOR = "org.apache.skywalking.apm.plugin.jdbc.mysql.v6.ConnectionCreateNewInterceptor";

    private static final String GET_INSTANCE_OLD_INTERCEPTOR = "org.apache.skywalking.apm.plugin.jdbc.mysql.v6.ConnectionCreateOldInterceptor";

    @Override
    public StaticMethodsInterceptPoint[] getStaticMethodsInterceptPoints() {
        return new StaticMethodsInterceptPoint[] {
            new StaticMethodsInterceptPoint() {
                @Override
                public ElementMatcher<MethodDescription> getMethodsMatcher() {
                    return named(CONNECT_METHOD).and(takesArguments(1));
                }

                @Override
                public String getMethodsInterceptor() {
                    return GET_INSTANCE_NEW_INTERCEPTOR;
                }

                @Override
                public boolean isOverrideArgs() {
                    return false;
                }
            },
            new StaticMethodsInterceptPoint() {
                @Override
                public ElementMatcher<MethodDescription> getMethodsMatcher() {
                    return named(CONNECT_METHOD).and(takesArguments(4));
                }

                @Override
                public String getMethodsInterceptor() {
                    return GET_INSTANCE_OLD_INTERCEPTOR;
                }

                @Override
                public boolean isOverrideArgs() {
                    return false;
                }
            }
        };
    }

    @Override
    protected ClassMatch enhanceClass() {
        return byName(JDBC_ENHANCE_CLASS);
    }
}
