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

import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.ContextSnapshot;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;

/**
 * implements Callback and EnhancedInstance, for kafka callback in lambda expression
 */
public class CallbackAdapterInterceptor implements Callback, EnhancedInstance {

    /**
     * user Callback object
     */
    private CallbackCache callbackCache;

    public CallbackAdapterInterceptor(CallbackCache callbackCache) {
        this.callbackCache = callbackCache;
    }

    @Override
    public void onCompletion(RecordMetadata metadata, Exception exception) {
        ContextSnapshot snapshot = callbackCache.getSnapshot();
        AbstractSpan activeSpan = ContextManager.createLocalSpan("Kafka/Producer/Callback");
        SpanLayer.asMQ(activeSpan);
        activeSpan.setComponent(ComponentsDefine.KAFKA_PRODUCER);
        if (metadata != null) {
            Tags.MQ_TOPIC.set(activeSpan, metadata.topic());
        }
        ContextManager.continued(snapshot);

        try {
            callbackCache.getCallback().onCompletion(metadata, exception);
        } catch (Throwable t) {
            ContextManager.activeSpan().log(t);
            throw t;
        } finally {
            if (exception != null) {
                ContextManager.activeSpan().log(exception);
            }
            ContextManager.stopSpan();
        }
    }

    @Override
    public Object getSkyWalkingDynamicField() {
        return callbackCache;
    }

    @Override
    public void setSkyWalkingDynamicField(final Object value) {
    }
}