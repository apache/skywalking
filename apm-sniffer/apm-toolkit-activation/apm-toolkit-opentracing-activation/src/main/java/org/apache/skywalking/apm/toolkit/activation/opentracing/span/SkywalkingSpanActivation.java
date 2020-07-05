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

package org.apache.skywalking.apm.toolkit.activation.opentracing.span;

import java.util.Map;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.InstanceMethodsInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.ClassInstanceMethodsEnhancePluginDefine;
import org.apache.skywalking.apm.agent.core.plugin.match.NameMatch;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.ConstructorInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.match.ClassMatch;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static org.apache.skywalking.apm.agent.core.plugin.bytebuddy.ArgumentTypeNameMatch.takesArgumentWithType;

/**
 * {@link SkywalkingSpanActivation} defines five interceptors to enhance the methods and constructor in class
 * <code>SkywalkingSpan</code>.
 * <p>
 * 1. The <code>ConstructorWithSpanBuilderInterceptor</code> interceptor enhance the constructor with
 * <code>SkywalkingSpanBuilder</code> argument.
 * <p>
 * 2. The <code>ConstructorWithTracerInterceptor</code> interceptor enhance the constructor with
 * <code>SkywalkingTracer</code> argument.
 * <p>
 * 3. The <code>SpanFinishInterceptor</code> interceptor enhance the <code>finish</code> method that the first argument
 * type is {@link Long}
 * <p>
 * 4. The <code>SpanLogInterceptor</code> interceptor enhance the <code>log</code> method that the first argument type
 * is {@link Long} and the second argument type is {@link Map}
 * <p>
 * 5. The <code>SpanSetOperationNameInterceptor</code> interceptor enhance the <code>setOperationName</code> method
 **/
public class SkywalkingSpanActivation extends ClassInstanceMethodsEnhancePluginDefine {

    private static final String ENHANCE_CLASS = "org.apache.skywalking.apm.toolkit.opentracing.SkywalkingSpan";

    private static final String SPAN_BUILDER_CLASS_NAME = "org.apache.skywalking.apm.toolkit.opentracing.SkywalkingSpanBuilder";
    private static final String CONSTRUCTOR_WITH_SPAN_BUILDER_INTERCEPTOR = "org.apache.skywalking.apm.toolkit.activation.opentracing.span.ConstructorWithSpanBuilderInterceptor";

    private static final String SKYWALKING_TRACER_CLASS_NAME = "org.apache.skywalking.apm.toolkit.opentracing.SkywalkingTracer";
    private static final String CONSTRUCTOR_WITH_TRACER_INTERCEPTOR = "org.apache.skywalking.apm.toolkit.activation.opentracing.span.ConstructorWithTracerInterceptor";

    private static final String FINISH_METHOD_INTERCEPTOR = "org.apache.skywalking.apm.toolkit.activation.opentracing.span.SpanFinishInterceptor";
    private static final String LOG_INTERCEPTOR = "org.apache.skywalking.apm.toolkit.activation.opentracing.span.SpanLogInterceptor";
    private static final String SET_OPERATION_NAME_INTERCEPTOR = "org.apache.skywalking.apm.toolkit.activation.opentracing.span.SpanSetOperationNameInterceptor";
    private static final String SET_TAG_INTERCEPTOR = "org.apache.skywalking.apm.toolkit.activation.opentracing.span.SpanSetTagInterceptor";

    @Override
    protected ClassMatch enhanceClass() {
        return NameMatch.byName(ENHANCE_CLASS);
    }

    @Override
    public ConstructorInterceptPoint[] getConstructorsInterceptPoints() {
        return new ConstructorInterceptPoint[] {
            new ConstructorInterceptPoint() {
                @Override
                public ElementMatcher<MethodDescription> getConstructorMatcher() {
                    return takesArgumentWithType(0, SPAN_BUILDER_CLASS_NAME);
                }

                @Override
                public String getConstructorInterceptor() {
                    return CONSTRUCTOR_WITH_SPAN_BUILDER_INTERCEPTOR;
                }
            },
            new ConstructorInterceptPoint() {
                @Override
                public ElementMatcher<MethodDescription> getConstructorMatcher() {
                    return takesArgumentWithType(0, SKYWALKING_TRACER_CLASS_NAME);
                }

                @Override
                public String getConstructorInterceptor() {
                    return CONSTRUCTOR_WITH_TRACER_INTERCEPTOR;
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
                    return named("finish").and(takesArgument(0, long.class));
                }

                @Override
                public String getMethodsInterceptor() {
                    return FINISH_METHOD_INTERCEPTOR;
                }

                @Override
                public boolean isOverrideArgs() {
                    return false;
                }
            },
            new InstanceMethodsInterceptPoint() {
                @Override
                public ElementMatcher<MethodDescription> getMethodsMatcher() {
                    return named("log").and(takesArgument(0, long.class).and(takesArgument(1, Map.class)));
                }

                @Override
                public String getMethodsInterceptor() {
                    return LOG_INTERCEPTOR;
                }

                @Override
                public boolean isOverrideArgs() {
                    return false;
                }
            },
            new InstanceMethodsInterceptPoint() {
                @Override
                public ElementMatcher<MethodDescription> getMethodsMatcher() {
                    return named("setOperationName");
                }

                @Override
                public String getMethodsInterceptor() {
                    return SET_OPERATION_NAME_INTERCEPTOR;
                }

                @Override
                public boolean isOverrideArgs() {
                    return false;
                }
            },
            new InstanceMethodsInterceptPoint() {
                @Override
                public ElementMatcher<MethodDescription> getMethodsMatcher() {
                    return named("setTag").and(takesArgument(0, String.class)).and(takesArgument(1, String.class));
                }

                @Override
                public String getMethodsInterceptor() {
                    return SET_TAG_INTERCEPTOR;
                }

                @Override
                public boolean isOverrideArgs() {
                    return false;
                }
            }
        };
    }
}
