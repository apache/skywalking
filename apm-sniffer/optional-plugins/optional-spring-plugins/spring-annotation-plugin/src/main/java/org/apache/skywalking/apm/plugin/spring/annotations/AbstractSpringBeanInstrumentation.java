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

package org.apache.skywalking.apm.plugin.spring.annotations;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.ConstructorInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.DeclaredInstanceMethodsInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.InstanceMethodsInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.ClassInstanceMethodsEnhancePluginDefine;
import org.apache.skywalking.apm.agent.core.plugin.match.ClassMatch;
import org.apache.skywalking.apm.agent.core.plugin.match.logical.LogicalMatchOperation;
import org.apache.skywalking.apm.util.StringUtil;

import static net.bytebuddy.matcher.ElementMatchers.isDeclaredBy;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static org.apache.skywalking.apm.agent.core.plugin.match.ClassAnnotationMatch.byClassAnnotationMatch;
import static org.apache.skywalking.apm.agent.core.plugin.match.RegexMatch.byRegexMatch;
import static org.apache.skywalking.apm.plugin.spring.annotations.SpringAnnotationConfig.Plugin.SpringAnnotation.CLASSNAME_MATCH_REGEX;

public abstract class AbstractSpringBeanInstrumentation extends ClassInstanceMethodsEnhancePluginDefine {
    private static final String INTERCEPTOR_CLASS = "org.apache.skywalking.apm.plugin.spring.annotations.SpringAnnotationInterceptor";
    public static final String INTERCEPT_GET_SKYWALKING_DYNAMIC_FIELD_METHOD = "getSkyWalkingDynamicField";
    public static final String INTERCEPT_SET_SKYWALKING_DYNAMIC_FIELD_METHOD = "setSkyWalkingDynamicField";

    @Override
    public final ConstructorInterceptPoint[] getConstructorsInterceptPoints() {
        return new ConstructorInterceptPoint[0];
    }

    @Override
    public final InstanceMethodsInterceptPoint[] getInstanceMethodsInterceptPoints() {
        return new InstanceMethodsInterceptPoint[] {
            new DeclaredInstanceMethodsInterceptPoint() {
                @Override
                public ElementMatcher<MethodDescription> getMethodsMatcher() {
                    return isPublic().and(not(isDeclaredBy(Object.class))
                                              .and(not(named(INTERCEPT_GET_SKYWALKING_DYNAMIC_FIELD_METHOD)))
                                              .and(not(named(INTERCEPT_SET_SKYWALKING_DYNAMIC_FIELD_METHOD))));
                }

                @Override
                public String getMethodsInterceptor() {
                    return INTERCEPTOR_CLASS;
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
        if (StringUtil.isEmpty(CLASSNAME_MATCH_REGEX)) {
            return byClassAnnotationMatch(getEnhanceAnnotation());
        } else {
            return LogicalMatchOperation.and(
                byRegexMatch(CLASSNAME_MATCH_REGEX.split(",")),
                byClassAnnotationMatch(getEnhanceAnnotation())
            );
        }
    }

    protected abstract String getEnhanceAnnotation();

}
