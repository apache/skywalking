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

package org.apache.skywalking.apm.plugin.hbase.define;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.ConstructorInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.InstanceMethodsInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.ClassInstanceMethodsEnhancePluginDefine;
import org.apache.skywalking.apm.agent.core.plugin.match.ClassMatch;
import org.apache.skywalking.apm.agent.core.plugin.match.NameMatch;

import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;
import static org.apache.skywalking.apm.agent.core.plugin.bytebuddy.ArgumentTypeNameMatch.takesArgumentWithType;

/**
 * There have several interceptors to adapt different version hbase client. We use the minimal compatible version to
 * name the Interceptor. eg.
 * <p>HTable100Interceptor, 100 means version 1.0.0, compatible with version [1.0.0, 2.0.0)</p>
 * <p>HTable200Interceptor, 200 means version 2.0.0, compatible with version [2.0.0, 2.2.0)</p>
 * <p>HTable220Interceptor, 220 means version 2.2.0, compatible with version [2.2.0, )</p>
 */
public class HTableInstrumentation extends ClassInstanceMethodsEnhancePluginDefine {

    private static final String ENHANCE_CLASS = "org.apache.hadoop.hbase.client.HTable";
    private static final String INTERCEPT_CLASS = "org.apache.skywalking.apm.plugin.hbase.HTableInterceptor";
    private static final String INTERCEPT_CLASS_100 = "org.apache.skywalking.apm.plugin.hbase.HTable100Interceptor";
    private static final String INTERCEPT_CLASS_200 = "org.apache.skywalking.apm.plugin.hbase.HTable200Interceptor";
    private static final String INTERCEPT_CLASS_220 = "org.apache.skywalking.apm.plugin.hbase.HTable220Interceptor";

    @Override
    protected ClassMatch enhanceClass() {
        return NameMatch.byName(ENHANCE_CLASS);
    }

    @Override
    public ConstructorInterceptPoint[] getConstructorsInterceptPoints() {
        return new ConstructorInterceptPoint[] {
            // compatible with version [1.0.0, 2.0.0)
            new ConstructorInterceptPoint() {
                @Override
                public ElementMatcher<MethodDescription> getConstructorMatcher() {
                    return takesArguments(6)
                        .and(takesArgumentWithType(0, "org.apache.hadoop.hbase.TableName"));
                }

                @Override
                public String getConstructorInterceptor() {
                    return INTERCEPT_CLASS_100;
                }
            },
            // compatible with version [2.0.0, 2.2.0)
            new ConstructorInterceptPoint() {
                @Override
                public ElementMatcher<MethodDescription> getConstructorMatcher() {
                    return takesArguments(5)
                        .and(takesArgumentWithType(0, "org.apache.hadoop.hbase.client.ClusterConnection"));
                }

                @Override
                public String getConstructorInterceptor() {
                    return INTERCEPT_CLASS_200;
                }
            },
            // compatible with version [2.2.0, )
            new ConstructorInterceptPoint() {
                @Override
                public ElementMatcher<MethodDescription> getConstructorMatcher() {
                    return takesArguments(5)
                        .and(takesArgumentWithType(0, "org.apache.hadoop.hbase.client.ConnectionImplementation"));
                }

                @Override
                public String getConstructorInterceptor() {
                    return INTERCEPT_CLASS_220;
                }
            }
        };
    }

    @Override
    public InstanceMethodsInterceptPoint[] getInstanceMethodsInterceptPoints() {
        return new InstanceMethodsInterceptPoint[] {
            new InstanceMethodsInterceptPoint() {
                @Override
                public ElementMatcher<MethodDescription> getMethodsMatcher() {
                    return named("delete")
                        .or(named("put"))
                        .or(isPublic().and(named("get")))
                        .or(named("getScanner")
                                .and(takesArguments(1))
                                .and(takesArgument(0, named("org.apache.hadoop.hbase.client.Scan"))));
                }

                @Override()
                public String getMethodsInterceptor() {
                    return INTERCEPT_CLASS;
                }

                @Override
                public boolean isOverrideArgs() {
                    return false;
                }
            }
        };
    }
}
