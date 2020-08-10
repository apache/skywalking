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

package org.apache.skywalking.apm.plugin.jdbc.mariadb.v2.define;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.ConstructorInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.InstanceMethodsInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.ClassInstanceMethodsEnhancePluginDefine;
import org.apache.skywalking.apm.agent.core.plugin.match.ClassMatch;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static org.apache.skywalking.apm.agent.core.plugin.match.MultiClassNameMatch.byMultiClassMatch;

public class PreparedStatementInstrumentation extends ClassInstanceMethodsEnhancePluginDefine {

    private static final String CLIENT_SIDE_PREPARED_STATEMENT_CLASS_2_0_X = "org.mariadb.jdbc.MariaDbPreparedStatementClient";
    private static final String CLIENT_SIDE_PREPARED_STATEMENT_CLASS_2_4_X = "org.mariadb.jdbc.ClientSidePreparedStatement";
    private static final String SERVER_SIDE_PREPARED_STATEMENT_CLASS_2_0_X = "org.mariadb.jdbc.MariaDbPreparedStatementServer";
    private static final String SERVER_SIDE_PREPARED_STATEMENT_CLASS_2_4_X = "org.mariadb.jdbc.ServerSidePreparedStatement";
    private static final String PREPARED_STATEMENT_EXECUTE_METHODS_INTERCEPTOR_CLASS = "org.apache.skywalking.apm.plugin.jdbc.mariadb.v2.PreparedStatementExecuteMethodsInterceptor";

    @Override
    protected ClassMatch enhanceClass() {
        return byMultiClassMatch(
                CLIENT_SIDE_PREPARED_STATEMENT_CLASS_2_0_X,
                CLIENT_SIDE_PREPARED_STATEMENT_CLASS_2_4_X,
                SERVER_SIDE_PREPARED_STATEMENT_CLASS_2_0_X,
                SERVER_SIDE_PREPARED_STATEMENT_CLASS_2_4_X
        );
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
                        return named("execute")
                                .or(named("executeQuery"))
                                .or(named("executeUpdate"));
                    }

                    @Override
                    public String getMethodsInterceptor() {
                        return PREPARED_STATEMENT_EXECUTE_METHODS_INTERCEPTOR_CLASS;
                    }

                    @Override
                    public boolean isOverrideArgs() {
                        return false;
                    }
                }
        };

    }
}
