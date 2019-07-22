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

package org.apache.skywalking.apm.plugin.jre.httpurlconnection;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.bootstrap.BootstrapClassEnhancePluginDefine;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.bootstrap.BootstrapInstanceMethodsInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.match.ClassMatch;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static org.apache.skywalking.apm.agent.core.plugin.match.NameMatch.byName;

/**
 * @author wusheng
 */
public class HttpUrlConnectionInstrumentation extends BootstrapClassEnhancePluginDefine {
    private static String CLASS_NAME = "java.net.HttpURLConnection";

    @Override protected ClassMatch enhanceClass() {
        return byName(CLASS_NAME);
    }

    @Override public BootstrapInstanceMethodsInterceptPoint[] getInstanceMethodsInterceptPoints() {
        return new BootstrapInstanceMethodsInterceptPoint[] {
            new BootstrapInstanceMethodsInterceptPoint() {
                @Override public ElementMatcher<MethodDescription> getMethodsMatcher() {
                    return named("setRequestMethod");
                }

                @Override public Class getMethodsInterceptor() {
                    return Interceptor.class;
                }

                @Override public boolean isOverrideArgs() {
                    return false;
                }
            }
        };
    }
}
