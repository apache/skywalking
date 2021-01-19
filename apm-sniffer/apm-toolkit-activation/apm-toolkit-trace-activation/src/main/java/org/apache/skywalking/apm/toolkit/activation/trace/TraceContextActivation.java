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
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.ClassStaticMethodsEnhancePluginDefine;
import org.apache.skywalking.apm.agent.core.plugin.match.ClassMatch;
import org.apache.skywalking.apm.agent.core.plugin.match.NameMatch;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.StaticMethodsInterceptPoint;

import static net.bytebuddy.matcher.ElementMatchers.named;

/**
 * Active the toolkit class "TraceContext". Should not dependency or import any class in
 * "skywalking-toolkit-trace-context" module. Activation's classloader is diff from "TraceContext", using direct will
 * trigger classloader issue.
 * <p>
 */
public class TraceContextActivation extends ClassStaticMethodsEnhancePluginDefine {

    public static final String TRACE_ID_INTERCEPT_CLASS = "org.apache.skywalking.apm.toolkit.activation.trace.TraceIDInterceptor";
    public static final String SEGMENT_ID_INTERCEPT_CLASS = "org.apache.skywalking.apm.toolkit.activation.trace.SegmentIDInterceptor";
    public static final String SPAN_ID_INTERCEPT_CLASS = "org.apache.skywalking.apm.toolkit.activation.trace.SpanIDInterceptor";
    public static final String ENHANCE_CLASS = "org.apache.skywalking.apm.toolkit.trace.TraceContext";
    public static final String ENHANCE_TRACE_ID_METHOD = "traceId";
    public static final String ENHANCE_SEGMENT_ID_METHOD = "segmentId";
    public static final String ENHANCE_SPAN_ID_METHOD = "spanId";
    public static final String ENHANCE_GET_CORRELATION_METHOD = "getCorrelation";
    public static final String INTERCEPT_GET_CORRELATION_CLASS = "org.apache.skywalking.apm.toolkit.activation.trace.CorrelationContextGetInterceptor";
    public static final String ENHANCE_PUT_CORRELATION_METHOD = "putCorrelation";
    public static final String INTERCEPT_PUT_CORRELATION_CLASS = "org.apache.skywalking.apm.toolkit.activation.trace.CorrelationContextPutInterceptor";

    /**
     * @return the target class, which needs active.
     */
    @Override
    protected ClassMatch enhanceClass() {
        return NameMatch.byName(ENHANCE_CLASS);
    }

    /**
     * @return the collection of {@link StaticMethodsInterceptPoint}, represent the intercepted methods and their
     * interceptors.
     */
    @Override
    public StaticMethodsInterceptPoint[] getStaticMethodsInterceptPoints() {
        return new StaticMethodsInterceptPoint[] {
            new StaticMethodsInterceptPoint() {
                @Override
                public ElementMatcher<MethodDescription> getMethodsMatcher() {
                    return named(ENHANCE_TRACE_ID_METHOD);
                }

                @Override
                public String getMethodsInterceptor() {
                    return TRACE_ID_INTERCEPT_CLASS;
                }

                @Override
                public boolean isOverrideArgs() {
                    return false;
                }
            },
            new StaticMethodsInterceptPoint() {
                @Override
                public ElementMatcher<MethodDescription> getMethodsMatcher() {
                    return named(ENHANCE_SEGMENT_ID_METHOD);
                }

                @Override
                public String getMethodsInterceptor() {
                    return SEGMENT_ID_INTERCEPT_CLASS;
                }

                @Override
                public boolean isOverrideArgs() {
                    return false;
                }
            },
            new StaticMethodsInterceptPoint() {
                @Override
                public ElementMatcher<MethodDescription> getMethodsMatcher() {
                    return named(ENHANCE_SPAN_ID_METHOD);
                }

                @Override
                public String getMethodsInterceptor() {
                    return SPAN_ID_INTERCEPT_CLASS;
                }

                @Override
                public boolean isOverrideArgs() {
                    return false;
                }
            },

            new StaticMethodsInterceptPoint() {
                @Override
                public ElementMatcher<MethodDescription> getMethodsMatcher() {
                    return named(ENHANCE_GET_CORRELATION_METHOD);
                }

                @Override
                public String getMethodsInterceptor() {
                    return INTERCEPT_GET_CORRELATION_CLASS;
                }

                @Override
                public boolean isOverrideArgs() {
                    return false;
                }
            },
            new StaticMethodsInterceptPoint() {
                @Override
                public ElementMatcher<MethodDescription> getMethodsMatcher() {
                    return named(ENHANCE_PUT_CORRELATION_METHOD);
                }

                @Override
                public String getMethodsInterceptor() {
                    return INTERCEPT_PUT_CORRELATION_CLASS;
                }

                @Override
                public boolean isOverrideArgs() {
                    return false;
                }
            }
        };
    }
}
