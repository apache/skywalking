/*
 * Copyright 2017, OpenSkywalking Organization All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Project repository: https://github.com/OpenSkywalking/skywalking
 */

package org.skywalking.apm.plugin.jdbc.mysql.define;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.skywalking.apm.agent.core.plugin.interceptor.ConstructorInterceptPoint;
import org.skywalking.apm.agent.core.plugin.interceptor.InstanceMethodsInterceptPoint;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.ClassInstanceMethodsEnhancePluginDefine;
import org.skywalking.apm.agent.core.plugin.match.ClassMatch;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;
import static org.skywalking.apm.plugin.jdbc.define.Constants.CLOSE_METHOD_NAME;
import static org.skywalking.apm.plugin.jdbc.define.Constants.COMMIT_METHOD_NAME;
import static org.skywalking.apm.plugin.jdbc.define.Constants.CREATE_STATEMENT_METHOD_NAME;
import static org.skywalking.apm.plugin.jdbc.define.Constants.PREPARE_CALL_METHOD_NAME;
import static org.skywalking.apm.plugin.jdbc.define.Constants.PREPARE_STATEMENT_METHOD_NAME;
import static org.skywalking.apm.plugin.jdbc.define.Constants.RELEASE_SAVE_POINT_METHOD_NAME;
import static org.skywalking.apm.plugin.jdbc.define.Constants.ROLLBACK_METHOD_NAME;
import static org.skywalking.apm.plugin.jdbc.define.Constants.SERVICE_METHOD_INTERCEPT_CLASS;
import static org.skywalking.apm.plugin.jdbc.mysql.define.MultiClassNameMatch.byMultiClassMatch;

/**
 * {@link ConnectionInstrumentation} intercepts the following methods that the class which extend {@link
 * com.mysql.jdbc.ConnectionImpl}. <br/>
 *
 * 1. Enhance <code>prepareStatement</code> by <code>org.skywalking.apm.plugin.jdbc.define.JDBCPrepareStatementInterceptor</code>
 * 2. Enhance <code>prepareCall</code> by <code>org.skywalking.apm.plugin.jdbc.define.JDBCPrepareCallInterceptor</code>
 * 3. Enhance <code>createStatement</code> by <code>org.skywalking.apm.plugin.jdbc.define.JDBCStatementInterceptor</code>
 * 4. Enhance <code>commit, rollback, close, releaseSavepoint</code> by <code>org.skywalking.apm.plugin.jdbc.define.ConnectionServiceMethodInterceptor</code>
 *
 * @author zhangxin
 */
public class ConnectionInstrumentation extends ClassInstanceMethodsEnhancePluginDefine {

    public static final String ENHANCE_CLASS = "com.mysql.jdbc.ConnectionImpl";

    @Override protected ConstructorInterceptPoint[] getConstructorsInterceptPoints() {
        return new ConstructorInterceptPoint[0];
    }

    @Override protected InstanceMethodsInterceptPoint[] getInstanceMethodsInterceptPoints() {
        return new InstanceMethodsInterceptPoint[] {
            new InstanceMethodsInterceptPoint() {
                @Override public ElementMatcher<MethodDescription> getMethodsMatcher() {
                    return named(PREPARE_STATEMENT_METHOD_NAME);
                }

                @Override public String getMethodsInterceptor() {
                    return "org.skywalking.apm.plugin.jdbc.mysql.CreatePreparedStatementInterceptor";
                }

                @Override public boolean isOverrideArgs() {
                    return false;
                }
            },
            new InstanceMethodsInterceptPoint() {
                @Override public ElementMatcher<MethodDescription> getMethodsMatcher() {
                    return named(PREPARE_CALL_METHOD_NAME);
                }

                @Override public String getMethodsInterceptor() {
                    return "org.skywalking.apm.plugin.jdbc.mysql.CreateCallableStatementInterceptor";
                }

                @Override public boolean isOverrideArgs() {
                    return false;
                }
            },
            new InstanceMethodsInterceptPoint() {
                @Override public ElementMatcher<MethodDescription> getMethodsMatcher() {
                    return named(CREATE_STATEMENT_METHOD_NAME).and(takesArguments(2));
                }

                @Override public String getMethodsInterceptor() {
                    return "org.skywalking.apm.plugin.jdbc.mysql.CreateStatementInterceptor";
                }

                @Override public boolean isOverrideArgs() {
                    return false;
                }
            },
            new InstanceMethodsInterceptPoint() {
                @Override public ElementMatcher<MethodDescription> getMethodsMatcher() {
                    return named(COMMIT_METHOD_NAME).or(named(ROLLBACK_METHOD_NAME)).or(named(CLOSE_METHOD_NAME)).or(named(RELEASE_SAVE_POINT_METHOD_NAME));
                }

                @Override public String getMethodsInterceptor() {
                    return SERVICE_METHOD_INTERCEPT_CLASS;
                }

                @Override public boolean isOverrideArgs() {
                    return false;
                }
            }
        };

    }

    @Override protected ClassMatch enhanceClass() {
        return byMultiClassMatch(ENHANCE_CLASS, "com.mysql.cj.jdbc.ConnectionImpl");
    }
}
