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


package org.apache.skywalking.apm.plugin.jdbc.oracle.define;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.skywalking.apm.agent.core.plugin.bytebuddy.ArgumentTypeNameMatch;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.ConstructorInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.InstanceMethodsInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.ClassInstanceMethodsEnhancePluginDefine;
import org.apache.skywalking.apm.agent.core.plugin.match.ClassMatch;
import org.apache.skywalking.apm.agent.core.plugin.match.NameMatch;
import org.apache.skywalking.apm.plugin.jdbc.define.Constants;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

/**
 * {@link ConnectionInstrumentation} intercept the following methods that the class which extend {@link
 * oracle.jdbc.driver.PhysicalConnection}. <br/>
 *
 * 1. Enhance <code>prepareStatement</code> by <code>org.apache.skywalking.apm.plugin.jdbc.define.JDBCPrepareStatementInterceptor</code>
 * 2. Enhance <code>prepareStatement</code> that the seconds argument type is <code>java.lang.String[]</code> by
 * <code>oracle.jdbc.driver.JDBCPrepareStatementWithArrayInterceptor</code>
 * 3.  Enhance <code>prepareStatement</code> that the seconds argument type is <code>int[]</code> by
 * <code>oracle.jdbc.driver.JDBCPrepareStatementWithArrayInterceptor</code>
 * 4. Enhance <code>prepareCall</code> by
 * <code>org.apache.skywalking.apm.plugin.jdbc.define.JDBCPrepareCallInterceptor</code>
 * 5. Enhance <code>createStatement</code>
 * by <code>org.apache.skywalking.apm.plugin.jdbc.define.JDBCStatementInterceptor</code>
 * 6. Enhance <code>commit, rollback, close, releaseSavepoint</code> by <code>org.apache.skywalking.apm.plugin.jdbc.define.ConnectionServiceMethodInterceptor</code>
 *
 * @author zhangxin
 */
public class ConnectionInstrumentation extends ClassInstanceMethodsEnhancePluginDefine {

    private static final String PREPARE_STATEMENT_METHOD_WITH_ARRAY_INTERCEPTOR_CLASS = "oracle.jdbc.driver.JDBCPrepareStatementWithArrayInterceptor";
    public static final String ENHANCE_CLASS = "oracle.jdbc.driver.PhysicalConnection";
    public static final String STRING_ARRAY_ARGUMENT_TYPE = "java.lang.String[]";
    public static final String INT_ARRAY_ARGUMENT_TYPE = "int[]";

    @Override protected ConstructorInterceptPoint[] getConstructorsInterceptPoints() {
        return new ConstructorInterceptPoint[0];
    }

    @Override protected InstanceMethodsInterceptPoint[] getInstanceMethodsInterceptPoints() {
        return new InstanceMethodsInterceptPoint[] {
            new InstanceMethodsInterceptPoint() {
                @Override public ElementMatcher<MethodDescription> getMethodsMatcher() {
                    return named(Constants.PREPARE_STATEMENT_METHOD_NAME).and(takesArguments(3));
                }

                @Override public String getMethodsInterceptor() {
                    return Constants.PREPARE_STATEMENT_INTERCEPT_CLASS;
                }

                @Override public boolean isOverrideArgs() {
                    return false;
                }
            },
            new InstanceMethodsInterceptPoint() {
                @Override public ElementMatcher<MethodDescription> getMethodsMatcher() {
                    return named(Constants.PREPARE_STATEMENT_METHOD_NAME).and(ArgumentTypeNameMatch.takesArgumentWithType(1, STRING_ARRAY_ARGUMENT_TYPE));
                }

                @Override public String getMethodsInterceptor() {
                    return PREPARE_STATEMENT_METHOD_WITH_ARRAY_INTERCEPTOR_CLASS;
                }

                @Override public boolean isOverrideArgs() {
                    return false;
                }
            },
            new InstanceMethodsInterceptPoint() {
                @Override public ElementMatcher<MethodDescription> getMethodsMatcher() {
                    return named(Constants.PREPARE_STATEMENT_METHOD_NAME).and(ArgumentTypeNameMatch.takesArgumentWithType(1, INT_ARRAY_ARGUMENT_TYPE));
                }

                @Override public String getMethodsInterceptor() {
                    return PREPARE_STATEMENT_METHOD_WITH_ARRAY_INTERCEPTOR_CLASS;
                }

                @Override public boolean isOverrideArgs() {
                    return false;
                }
            },
            new InstanceMethodsInterceptPoint() {
                @Override public ElementMatcher<MethodDescription> getMethodsMatcher() {
                    return named(Constants.PREPARE_CALL_METHOD_NAME).and(takesArguments(3));
                }

                @Override public String getMethodsInterceptor() {
                    return Constants.PREPARE_CALL_INTERCEPT_CLASS;
                }

                @Override public boolean isOverrideArgs() {
                    return false;
                }
            },
            new InstanceMethodsInterceptPoint() {
                @Override public ElementMatcher<MethodDescription> getMethodsMatcher() {
                    return named(Constants.CREATE_STATEMENT_METHOD_NAME).and(takesArguments(2));
                }

                @Override public String getMethodsInterceptor() {
                    return Constants.CREATE_STATEMENT_INTERCEPT_CLASS;
                }

                @Override public boolean isOverrideArgs() {
                    return false;
                }
            },
            new InstanceMethodsInterceptPoint() {
                @Override public ElementMatcher<MethodDescription> getMethodsMatcher() {
                    return named(Constants.COMMIT_METHOD_NAME).or(named(Constants.ROLLBACK_METHOD_NAME)).or(named(Constants.CLOSE_METHOD_NAME)).or(named(Constants.RELEASE_SAVE_POINT_METHOD_NAME));
                }

                @Override public String getMethodsInterceptor() {
                    return Constants.SERVICE_METHOD_INTERCEPT_CLASS;
                }

                @Override public boolean isOverrideArgs() {
                    return false;
                }
            }
        };
    }

    @Override protected ClassMatch enhanceClass() {
        return NameMatch.byName(ENHANCE_CLASS);
    }
}
