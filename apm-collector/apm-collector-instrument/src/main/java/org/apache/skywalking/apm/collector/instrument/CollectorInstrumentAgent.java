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

package org.apache.skywalking.apm.collector.instrument;

import java.lang.instrument.Instrumentation;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.utility.JavaModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static net.bytebuddy.matcher.ElementMatchers.declaresMethod;
import static net.bytebuddy.matcher.ElementMatchers.isAnnotatedWith;
import static net.bytebuddy.matcher.ElementMatchers.named;

/**
 * There are a lot of monitoring requirements in collector side.
 * The agent way is easy, pluggable, and match the target services/graph-nodes automatically.
 *
 * This agent is designed and expected running in the same class loader of the collector application,
 * so I will keep all class loader issue out of concern,
 * in order to keep the trace and monitor codes as simple as possible.
 *
 * @author wu-sheng, peng-yongsheng
 */
public class CollectorInstrumentAgent {
    private final static Logger logger = LoggerFactory.getLogger(CollectorInstrumentAgent.class);

    public static void premain(String agentArgs, Instrumentation instrumentation) {
        logger.info("Collector performance instrument agent startup");

        new AgentBuilder.Default().type(
            declaresMethod(isAnnotationedMatch())
        ).transform((builder, typeDescription, classLoader, module) -> {
            builder = builder.method(isAnnotationedMatch())
                .intercept(MethodDelegation.withDefaultConfiguration()
                    .to(new ServiceMetricTracing()));
            return builder;
        }).with(new AgentBuilder.Listener() {
            @Override
            public void onDiscovery(String typeName, ClassLoader classLoader, JavaModule module, boolean loaded) {
            }

            @Override
            public void onTransformation(TypeDescription typeDescription, ClassLoader classLoader, JavaModule module,
                boolean loaded, DynamicType dynamicType) {
            }

            @Override
            public void onIgnored(TypeDescription typeDescription, ClassLoader classLoader, JavaModule module,
                boolean loaded) {
            }

            @Override public void onError(String typeName, ClassLoader classLoader, JavaModule module, boolean loaded,
                Throwable throwable) {
                logger.error("Enhance service " + typeName + " error.", throwable);
            }

            @Override
            public void onComplete(String typeName, ClassLoader classLoader, JavaModule module, boolean loaded) {
            }
        }).installOn(instrumentation);
    }

    private static ElementMatcher<? super MethodDescription> isAnnotationedMatch() {
        return isAnnotatedWith(named("org.apache.skywalking.apm.collector.core.annotations.trace.GraphComputingMetric"));
    }
}
