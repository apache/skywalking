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

package org.apache.skywalking.apm.plugin.kafka.v11.define;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.ConstructorInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.InstanceMethodsInterceptPoint;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.ClassInstanceMethodsEnhancePluginDefine;
import org.apache.skywalking.apm.agent.core.plugin.match.ClassMatch;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static org.apache.skywalking.apm.agent.core.plugin.bytebuddy.ArgumentTypeNameMatch.takesArgumentWithType;
import static org.apache.skywalking.apm.agent.core.plugin.match.NameMatch.byName;

/**
 * {@link KafkaProducerInstrumentation} intercept the method <code>send</code> in the class
 * <code>org.apache.kafka.clients.producer.KafkaProducer</code>. Here is the intercept process steps.
 *
 *
 * <pre>
 *  1. Record the broker address when the client create the <code>org.apache.kafka.clients.producer.KafkaProducer</code>
 * instance
 *  2. Fetch the topic name from <code>org.apache.kafka.clients.producer.ProducerRecord</code> when the client call
 * <code>send</code> method.
 *  3. Create the exit span when the client call <code>send</code> method
 *  4. Set the <code>Context</code> into the <code>org.apache.kafka.clients.producer.ProducerRecord#headers</code>
 *  5. Stop the exit span when end the <code>send</code> method.
 * </pre>
 *
 * @author zhang xin
 */
public class KafkaProducerInstrumentation extends ClassInstanceMethodsEnhancePluginDefine {

    public static final String INTERCEPTOR_CLASS = "org.apache.skywalking.apm.plugin.kafka.v11.KafkaProducerInterceptor";
    public static final String ENHANCE_CLASS = "org.apache.kafka.clients.producer.KafkaProducer";
    public static final String CONSTRUCTOR_INTERCEPTOR_CLASS = "org.apache.skywalking.apm.plugin.kafka.v11.ProducerConstructorInterceptor";
    public static final String CONSTRUCTOR_INTERCEPTOR_FLAG = "org.apache.kafka.clients.producer.ProducerConfig";

    @Override protected ConstructorInterceptPoint[] getConstructorsInterceptPoints() {
        return new ConstructorInterceptPoint[] {
            new ConstructorInterceptPoint() {
                @Override public ElementMatcher<MethodDescription> getConstructorMatcher() {
                    return takesArgumentWithType(0, CONSTRUCTOR_INTERCEPTOR_FLAG);
                }

                @Override public String getConstructorInterceptor() {
                    return CONSTRUCTOR_INTERCEPTOR_CLASS;
                }
            }
        };
    }

    @Override protected InstanceMethodsInterceptPoint[] getInstanceMethodsInterceptPoints() {
        return new InstanceMethodsInterceptPoint[] {
            new InstanceMethodsInterceptPoint() {
                @Override public ElementMatcher<MethodDescription> getMethodsMatcher() {
                    return named("doSend");
                }

                @Override public String getMethodsInterceptor() {
                    return INTERCEPTOR_CLASS;
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
