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

package org.apache.skywalking.apm.plugin.kafka.define;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.ConstructorInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.InstanceMethodsInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.match.ClassMatch;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static org.apache.skywalking.apm.agent.core.plugin.bytebuddy.ArgumentTypeNameMatch.takesArgumentWithType;
import static org.apache.skywalking.apm.agent.core.plugin.match.NameMatch.byName;

/**
 * Here is the intercept process steps.
 *
 * <pre>
 *  1. Record the topic when the client invoke <code>subscribed</code> method
 *  2. Create the entry span when the client invoke the method <code>pollOnce</code>.
 *  3. Extract all the <code>Trace Context</code> by iterate all <code>ConsumerRecord</code>
 *  4. Stop the entry span when <code>pollOnce</code> method finished.
 * </pre>
 *
 * @author zhang xin,stalary
 */
public class KafkaConsumerInstrumentation extends AbstractKafkaInstrumentation {

    public static final String CONSTRUCTOR_INTERCEPT_TYPE = "org.apache.kafka.clients.consumer.ConsumerConfig";
    public static final String CONSTRUCTOR_INTERCEPTOR_CLASS = "org.apache.skywalking.apm.plugin.kafka.ConsumerConstructorInterceptor";
    public static final String INTERCEPTOR_CLASS = "org.apache.skywalking.apm.plugin.kafka.KafkaConsumerInterceptor";
    public static final String ENHANCE_METHOD = "pollOnce";
    public static final String ENHANCE_COMPATIBLE_METHOD = "pollForFetches";
    public static final String ENHANCE_CLASS = "org.apache.kafka.clients.consumer.KafkaConsumer";
    public static final String SUBSCRIBE_METHOD = "subscribe";
    public static final String SUBSCRIBE_INTERCEPT_TYPE = "org.apache.kafka.clients.consumer.ConsumerRebalanceListener";
    public static final String SUBSCRIBE_INTERCEPT_CLASS = "org.apache.skywalking.apm.plugin.kafka.SubscribeMethodInterceptor";

    @Override public ConstructorInterceptPoint[] getConstructorsInterceptPoints() {
        return new ConstructorInterceptPoint[] {
            new ConstructorInterceptPoint() {
                @Override public ElementMatcher<MethodDescription> getConstructorMatcher() {
                    return takesArgumentWithType(0, CONSTRUCTOR_INTERCEPT_TYPE);
                }

                @Override public String getConstructorInterceptor() {
                    return CONSTRUCTOR_INTERCEPTOR_CLASS;
                }
            }
        };
    }

    @Override public InstanceMethodsInterceptPoint[] getInstanceMethodsInterceptPoints() {
        return new InstanceMethodsInterceptPoint[] {
            new InstanceMethodsInterceptPoint() {
                @Override public ElementMatcher<MethodDescription> getMethodsMatcher() {
                    return named(ENHANCE_METHOD).or(named(ENHANCE_COMPATIBLE_METHOD));
                }

                @Override public String getMethodsInterceptor() {
                    return INTERCEPTOR_CLASS;
                }

                @Override public boolean isOverrideArgs() {
                    return false;
                }
            },
            new InstanceMethodsInterceptPoint() {
                @Override public ElementMatcher<MethodDescription> getMethodsMatcher() {
                    return named(SUBSCRIBE_METHOD).and(takesArgumentWithType(1, SUBSCRIBE_INTERCEPT_TYPE));
                }

                @Override public String getMethodsInterceptor() {
                    return SUBSCRIBE_INTERCEPT_CLASS;
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
}
