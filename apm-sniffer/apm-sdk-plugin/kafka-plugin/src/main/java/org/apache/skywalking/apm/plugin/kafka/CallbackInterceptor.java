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

package org.apache.skywalking.apm.plugin.kafka;

import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.ContextSnapshot;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;

import java.lang.reflect.Method;

/**
 *
 **/
public class CallbackInterceptor implements InstanceMethodsAroundInterceptor {

    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
        MethodInterceptResult result) throws Throwable {
        CallbackCache cache = (CallbackCache) objInst.getSkyWalkingDynamicField();
        if (null != cache) {
            ContextSnapshot snapshot = getSnapshot(cache);
            RecordMetadata metadata = (RecordMetadata) allArguments[0];
            AbstractSpan activeSpan = ContextManager.createLocalSpan("Kafka/Producer/Callback");
            SpanLayer.asMQ(activeSpan);
            activeSpan.setComponent(ComponentsDefine.KAFKA_PRODUCER);
            if (metadata != null) {
                // Null if an error occurred during processing of this record
                Tags.MQ_TOPIC.set(activeSpan, metadata.topic());
            }
            ContextManager.continued(snapshot);
        }
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
        Object ret) throws Throwable {
        CallbackCache cache = (CallbackCache) objInst.getSkyWalkingDynamicField();
        if (null != cache) {
            ContextSnapshot snapshot = getSnapshot(cache);
            if (null != snapshot) {
                Exception exceptions = (Exception) allArguments[1];
                if (exceptions != null) {
                    ContextManager.activeSpan().log(exceptions);
                }
                ContextManager.stopSpan();
            }
        }
        return ret;
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
        Class<?>[] argumentsTypes, Throwable t) {
        CallbackCache cache = (CallbackCache) objInst.getSkyWalkingDynamicField();
        if (null != cache) {
            ContextManager.activeSpan().log(t);
        }
    }

    private ContextSnapshot getSnapshot(CallbackCache cache) {
        ContextSnapshot snapshot = cache.getSnapshot();
        if (snapshot == null) {
            snapshot = ((CallbackCache) ((EnhancedInstance) cache.getCallback()).getSkyWalkingDynamicField()).getSnapshot();
        }
        return snapshot;
    }
}
