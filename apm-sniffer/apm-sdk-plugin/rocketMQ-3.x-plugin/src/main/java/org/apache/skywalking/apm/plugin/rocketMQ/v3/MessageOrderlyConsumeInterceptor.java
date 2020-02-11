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

package org.apache.skywalking.apm.plugin.rocketMQ.v3;

import java.lang.reflect.Method;
import com.alibaba.rocketmq.client.consumer.listener.ConsumeOrderlyStatus;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;

/**
 * {@link MessageOrderlyConsumeInterceptor} set the process status after the {@link
 * com.alibaba.rocketmq.client.consumer.listener.MessageListenerOrderly#consumeMessage(java.util.List,
 * com.alibaba.rocketmq.client.consumer.listener.ConsumeOrderlyContext)} method execute.
 */
public class MessageOrderlyConsumeInterceptor extends AbstractMessageConsumeInterceptor {

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
        Object ret) throws Throwable {

        ConsumeOrderlyStatus status = (ConsumeOrderlyStatus) ret;
        if (status == ConsumeOrderlyStatus.SUSPEND_CURRENT_QUEUE_A_MOMENT) {
            AbstractSpan activeSpan = ContextManager.activeSpan();
            activeSpan.errorOccurred();
            Tags.STATUS_CODE.set(activeSpan, status.name());
        }
        ContextManager.stopSpan();
        return ret;
    }

}
