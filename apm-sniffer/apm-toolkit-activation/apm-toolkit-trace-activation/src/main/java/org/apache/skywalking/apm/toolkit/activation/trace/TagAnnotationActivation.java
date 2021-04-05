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

package org.apache.skywalking.apm.toolkit.activation.trace;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.ConstructorInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.DeclaredInstanceMethodsInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.InstanceMethodsInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.StaticMethodsInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.ClassEnhancePluginDefine;
import org.apache.skywalking.apm.agent.core.plugin.match.ClassMatch;

import static net.bytebuddy.matcher.ElementMatchers.isAnnotatedWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static org.apache.skywalking.apm.agent.core.plugin.match.MethodAnnotationMatch.byMethodAnnotationMatch;
import static org.apache.skywalking.apm.agent.core.plugin.match.logical.LogicalMatchOperation.and;
import static org.apache.skywalking.apm.agent.core.plugin.match.logical.LogicalMatchOperation.not;
import static org.apache.skywalking.apm.agent.core.plugin.match.logical.LogicalMatchOperation.or;

/**
 * Intercepts all methods annotated with {@link org.apache.skywalking.apm.toolkit.trace.Tag}
 */
public class TagAnnotationActivation extends ClassEnhancePluginDefine {

    public static final String TAG_ANNOTATION_METHOD_INTERCEPTOR = "org.apache.skywalking.apm.toolkit.activation.trace.TagAnnotationMethodInterceptor";
    public static final String TAG_ANNOTATION_STATIC_METHOD_INTERCEPTOR = "org.apache.skywalking.apm.toolkit.activation.trace.TagAnnotationStaticMethodInterceptor";
    public static final String TAG_ANNOTATION = "org.apache.skywalking.apm.toolkit.trace.Tag";
    public static final String TAGS_ANNOTATION = "org.apache.skywalking.apm.toolkit.trace.Tags";
    public static final String TRACE_ANNOTATION = "org.apache.skywalking.apm.toolkit.trace.Trace";

    @Override
    public ConstructorInterceptPoint[] getConstructorsInterceptPoints() {
        return new ConstructorInterceptPoint[0];
    }

    @Override
    public InstanceMethodsInterceptPoint[] getInstanceMethodsInterceptPoints() {
        return new InstanceMethodsInterceptPoint[] {
            new DeclaredInstanceMethodsInterceptPoint() {
                @Override
                public ElementMatcher<MethodDescription> getMethodsMatcher() {
                    return isAnnotatedWith(named(TAG_ANNOTATION));
                }

                @Override
                public String getMethodsInterceptor() {
                    return TAG_ANNOTATION_METHOD_INTERCEPTOR;
                }

                @Override
                public boolean isOverrideArgs() {
                    return false;
                }
            }
        };
    }

    @Override
    public StaticMethodsInterceptPoint[] getStaticMethodsInterceptPoints() {
        return new StaticMethodsInterceptPoint[]{
                new StaticMethodsInterceptPoint() {
                    @Override
                    public ElementMatcher<MethodDescription> getMethodsMatcher() {
                        return isAnnotatedWith(named(TAG_ANNOTATION));
                    }

                    @Override
                    public String getMethodsInterceptor() {
                        return TAG_ANNOTATION_STATIC_METHOD_INTERCEPTOR;
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
        return and(not(byMethodAnnotationMatch(TRACE_ANNOTATION)),

            or(byMethodAnnotationMatch(TAGS_ANNOTATION), byMethodAnnotationMatch(TAG_ANNOTATION)));
    }
}
