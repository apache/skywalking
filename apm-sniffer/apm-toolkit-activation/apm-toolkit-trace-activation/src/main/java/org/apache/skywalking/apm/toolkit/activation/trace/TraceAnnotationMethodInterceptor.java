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
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.agent.core.util.CustomizeExpression;
import org.apache.skywalking.apm.agent.core.util.MethodUtil;
import org.apache.skywalking.apm.toolkit.activation.ToolkitPluginConfig;
import org.apache.skywalking.apm.toolkit.activation.util.TagUtil;
import org.apache.skywalking.apm.toolkit.trace.Tag;
import org.apache.skywalking.apm.toolkit.trace.Tags;
import org.apache.skywalking.apm.toolkit.trace.Trace;

/**
 * {@link TraceAnnotationMethodInterceptor} create a local span and set the operation name which fetch from
 * <code>org.apache.skywalking.apm.toolkit.trace.annotation.Trace.operationName</code>. if the fetch value is blank
 * string, and the operation name will be the method name.
 */
public class TraceAnnotationMethodInterceptor implements InstanceMethodsAroundInterceptor {
    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
                             MethodInterceptResult result) throws Throwable {
        Trace trace = method.getAnnotation(Trace.class);
        String operationName = trace.operationName();
        if (operationName.length() == 0 || ToolkitPluginConfig.Plugin.Toolkit.USE_QUALIFIED_NAME_AS_OPERATION_NAME) {
            operationName = MethodUtil.generateOperationName(method);
        }

        final AbstractSpan localSpan = ContextManager.createLocalSpan(operationName);

        final Map<String, Object> context = CustomizeExpression.evaluationContext(allArguments);

        final Tags tags = method.getAnnotation(Tags.class);
        if (tags != null && tags.value().length > 0) {
            for (final Tag tag : tags.value()) {
                if (!TagUtil.isReturnTag(tag.value())) {
                    TagUtil.tagParamsSpan(localSpan, context, tag);
                }
            }
        }
        final Tag tag = method.getAnnotation(Tag.class);
        if (tag != null && !TagUtil.isReturnTag(tag.value())) {
            TagUtil.tagParamsSpan(localSpan, context, tag);
        }
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
                              Object ret) throws Throwable {
        try {
            if (ret == null) {
                return ret;
            }
            final AbstractSpan localSpan = ContextManager.activeSpan();
            final Map<String, Object> context = CustomizeExpression.evaluationReturnContext(ret);
            final Tags tags = method.getAnnotation(Tags.class);
            if (tags != null && tags.value().length > 0) {
                for (final Tag tag : tags.value()) {
                    if (TagUtil.isReturnTag(tag.value())) {
                        TagUtil.tagReturnSpanSpan(localSpan, context, tag);
                    }
                }
            }
            final Tag tag = method.getAnnotation(Tag.class);
            if (tag != null && TagUtil.isReturnTag(tag.value())) {
                TagUtil.tagReturnSpanSpan(localSpan, context, tag);
            }
        } finally {
            ContextManager.stopSpan();
        }
        return ret;
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
                                      Class<?>[] argumentsTypes, Throwable t) {
        ContextManager.activeSpan().log(t);
    }
}
