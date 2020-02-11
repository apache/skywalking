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

package org.apache.skywalking.apm.plugin.jdbc.h2.define;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.skywalking.apm.plugin.jdbc.define.Constants;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.ConstructorInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.InstanceMethodsInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.ClassInstanceMethodsEnhancePluginDefine;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

/**
 * {@link AbstractConnectionInstrumentation} define how to enhance the following methods that the class which extend
 * {@link java.sql.Connection}.
 * <p>
 * 1. Enhance <code>prepareStatement</code> by <code>org.apache.skywalking.apm.plugin.jdbc.define.JDBCPrepareStatementInterceptor</code>
 * 3. Enhance <code>prepareCall</code> by <code>org.apache.skywalking.apm.plugin.jdbc.define.JDBCPrepareCallInterceptor</code>
 * 4. Enhance <code>createStatement</code> by <code>org.apache.skywalking.apm.plugin.jdbc.define.JDBCStatementInterceptor</code>
 * 5. Enhance <code>commit, rollback, close, releaseSavepoint</code> by <code>org.apache.skywalking.apm.plugin.jdbc.define.ConnectionServiceMethodInterceptor</code>
 */
public abstract class AbstractConnectionInstrumentation extends ClassInstanceMethodsEnhancePluginDefine {

    @Override
    public ConstructorInterceptPoint[] getConstructorsInterceptPoints() {
        return new ConstructorInterceptPoint[0];
    }

    @Override
    public InstanceMethodsInterceptPoint[] getInstanceMethodsInterceptPoints() {
        return new InstanceMethodsInterceptPoint[] {
            new InstanceMethodsInterceptPoint() {
                @Override
                public ElementMatcher<MethodDescription> getMethodsMatcher() {
                    return named(Constants.PREPARE_STATEMENT_METHOD_NAME).and(takesArguments(1));
                }

                @Override
                public String getMethodsInterceptor() {
                    return Constants.PREPARE_STATEMENT_INTERCEPT_CLASS;
                }

                @Override
                public boolean isOverrideArgs() {
                    return false;
                }
            },
            new InstanceMethodsInterceptPoint() {
                @Override
                public ElementMatcher<MethodDescription> getMethodsMatcher() {
                    return named(Constants.PREPARE_STATEMENT_METHOD_NAME).and(takesArguments(3));
                }

                @Override
                public String getMethodsInterceptor() {
                    return Constants.PREPARE_STATEMENT_INTERCEPT_CLASS;
                }

                @Override
                public boolean isOverrideArgs() {
                    return false;
                }
            },
            new InstanceMethodsInterceptPoint() {
                @Override
                public ElementMatcher<MethodDescription> getMethodsMatcher() {
                    return named(Constants.PREPARE_STATEMENT_METHOD_NAME).and(takesArguments(4));
                }

                @Override
                public String getMethodsInterceptor() {
                    return Constants.PREPARE_STATEMENT_INTERCEPT_CLASS;
                }

                @Override
                public boolean isOverrideArgs() {
                    return false;
                }
            },
            new InstanceMethodsInterceptPoint() {
                @Override
                public ElementMatcher<MethodDescription> getMethodsMatcher() {
                    return named(Constants.PREPARE_CALL_METHOD_NAME);
                }

                @Override
                public String getMethodsInterceptor() {
                    return Constants.PREPARE_CALL_INTERCEPT_CLASS;
                }

                @Override
                public boolean isOverrideArgs() {
                    return false;
                }
            },
            new InstanceMethodsInterceptPoint() {
                @Override
                public ElementMatcher<MethodDescription> getMethodsMatcher() {
                    return named(Constants.CREATE_STATEMENT_METHOD_NAME);
                }

                @Override
                public String getMethodsInterceptor() {
                    return Constants.CREATE_STATEMENT_INTERCEPT_CLASS;
                }

                @Override
                public boolean isOverrideArgs() {
                    return false;
                }
            },
            new InstanceMethodsInterceptPoint() {
                @Override
                public ElementMatcher<MethodDescription> getMethodsMatcher() {
                    return named(Constants.COMMIT_METHOD_NAME).or(named(Constants.ROLLBACK_METHOD_NAME))
                                                              .or(named(Constants.CLOSE_METHOD_NAME))
                                                              .or(named(Constants.RELEASE_SAVE_POINT_METHOD_NAME));
                }

                @Override
                public String getMethodsInterceptor() {
                    return Constants.SERVICE_METHOD_INTERCEPT_CLASS;
                }

                @Override
                public boolean isOverrideArgs() {
                    return false;
                }
            }
        };
    }
}
