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

import java.lang.reflect.Method;
import java.util.Map;

import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.tag.StringTag;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.agent.core.util.CustomizeExpression;
import org.apache.skywalking.apm.toolkit.trace.Tag;
import org.apache.skywalking.apm.toolkit.trace.Tags;

/**
 * @author kezhenxu94
 */
public class TagAnnotationMethodInterceptor implements InstanceMethodsAroundInterceptor {
    @Override
    public void beforeMethod(
        final EnhancedInstance objInst,
        final Method method,
        final Object[] allArguments,
        final Class<?>[] argumentsTypes,
        final MethodInterceptResult result) {

        if (!ContextManager.isActive()) {
            return;
        }

        final AbstractSpan activeSpan = ContextManager.activeSpan();
        final Map<String, Object> context = CustomizeExpression.evaluationContext(allArguments);

        final Tags tags = method.getAnnotation(Tags.class);
        if (tags != null && tags.value().length > 0) {
            for (final Tag tag : tags.value()) {
                tagSpan(activeSpan, tag, context);
            }
        }

        final Tag tag = method.getAnnotation(Tag.class);
        if (tag != null) {
            tagSpan(activeSpan, tag, context);
        }
    }

    private void tagSpan(final AbstractSpan span, final Tag tag, final Map<String, Object> context) {
        new StringTag(tag.key()).set(span, CustomizeExpression.parseExpression(tag.value(), context));
    }

    @Override
    public Object afterMethod(
        final EnhancedInstance objInst,
        final Method method,
        final Object[] allArguments,
        final Class<?>[] argumentsTypes,
        final Object ret) {
        return ret;
    }

    @Override
    public void handleMethodException(
        final EnhancedInstance objInst,
        final Method method,
        final Object[] allArguments,
        final Class<?>[] argumentsTypes,
        final Throwable t) {
        if (ContextManager.isActive()) {
            ContextManager.activeSpan().errorOccurred().log(t);
        }
    }
}
