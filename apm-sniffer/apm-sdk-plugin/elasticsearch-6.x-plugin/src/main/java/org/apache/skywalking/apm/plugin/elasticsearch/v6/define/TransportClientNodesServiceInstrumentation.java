/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.apache.skywalking.apm.plugin.elasticsearch.v6.define;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.ConstructorInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.InstanceMethodsInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.ClassInstanceMethodsEnhancePluginDefine;
import org.apache.skywalking.apm.agent.core.plugin.match.ClassMatch;
import org.apache.skywalking.apm.plugin.elasticsearch.v6.interceptor.Constants;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static org.apache.skywalking.apm.agent.core.plugin.bytebuddy.ArgumentTypeNameMatch.takesArgumentWithType;
import static org.apache.skywalking.apm.agent.core.plugin.match.NameMatch.byName;

public class TransportClientNodesServiceInstrumentation extends ClassInstanceMethodsEnhancePluginDefine {

    public static final String INTERCEPTOR_CLASS = "org.apache.skywalking.apm.plugin.elasticsearch.v6.interceptor.TransportClientNodesServiceInterceptor";
    public static final String ADD_TRANSPORT_ADDRESSES_INTERCEPTOR = INTERCEPTOR_CLASS + "$AddTransportAddressesInterceptor";
    public static final String REMOVE_TRANSPORT_ADDRESS_INTERCEPTOR = INTERCEPTOR_CLASS + "$RemoveTransportAddressInterceptor";
    public static final String EXECUTE_INTERCEPTOR = INTERCEPTOR_CLASS + "$ExecuteInterceptor";

    public static final String ENHANCE_CLASS = "org.elasticsearch.client.transport.TransportClientNodesService";

    @Override public ConstructorInterceptPoint[] getConstructorsInterceptPoints() {
        return new ConstructorInterceptPoint[] {
            new ConstructorInterceptPoint() {
                @Override
                public ElementMatcher<MethodDescription> getConstructorMatcher() {
                    return takesArgumentWithType(1, "org.elasticsearch.transport.TransportService");
                }

                @Override
                public String getConstructorInterceptor() {
                    return INTERCEPTOR_CLASS;
                }
            }
        };
    }

    @Override public InstanceMethodsInterceptPoint[] getInstanceMethodsInterceptPoints() {
        return new InstanceMethodsInterceptPoint[]{
            new InstanceMethodsInterceptPoint() {
                @Override public ElementMatcher<MethodDescription> getMethodsMatcher() {
                    return named("addTransportAddresses");
                }

                @Override public String getMethodsInterceptor() {
                    return ADD_TRANSPORT_ADDRESSES_INTERCEPTOR;
                }

                @Override public boolean isOverrideArgs() {
                    return false;
                }
            },
            new InstanceMethodsInterceptPoint() {
                @Override public ElementMatcher<MethodDescription> getMethodsMatcher() {
                    return named("removeTransportAddress");
                }

                @Override public String getMethodsInterceptor() {
                    return REMOVE_TRANSPORT_ADDRESS_INTERCEPTOR;
                }

                @Override public boolean isOverrideArgs() {
                    return false;
                }
            },
            new InstanceMethodsInterceptPoint() {
                @Override public ElementMatcher<MethodDescription> getMethodsMatcher() {
                    return named("execute");
                }

                @Override public String getMethodsInterceptor() {
                    return EXECUTE_INTERCEPTOR;
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

    @Override
    protected String[] witnessClasses() {
        return new String[]{Constants.TASK_TRANSPORT_CHANNEL_WITNESS_CLASSES};
    }
}
