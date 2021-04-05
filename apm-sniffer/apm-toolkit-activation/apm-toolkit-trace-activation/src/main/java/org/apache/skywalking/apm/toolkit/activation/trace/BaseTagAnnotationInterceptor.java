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

import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.util.CustomizeExpression;
import org.apache.skywalking.apm.toolkit.activation.util.TagUtil;
import org.apache.skywalking.apm.toolkit.trace.Tag;
import org.apache.skywalking.apm.toolkit.trace.Tags;

import java.lang.reflect.Method;
import java.util.Map;

public class BaseTagAnnotationInterceptor {
    void beforeMethod(Method method, Object[] allArguments) {
        if (!ContextManager.isActive()) {
            return;
        }
        final AbstractSpan activeSpan = ContextManager.activeSpan();
        final Map<String, Object> context = CustomizeExpression.evaluationContext(allArguments);

        final Tags tags = method.getAnnotation(Tags.class);
        if (tags != null && tags.value().length > 0) {
            for (final Tag tag : tags.value()) {
                if (!TagUtil.isReturnTag(tag.value())) {
                    TagUtil.tagParamsSpan(activeSpan, context, tag);
                }
            }
        }
        final Tag tag = method.getAnnotation(Tag.class);
        if (tag != null && !TagUtil.isReturnTag(tag.value())) {
            TagUtil.tagParamsSpan(activeSpan, context, tag);
        }
    }

    void afterMethod(Method method, Object ret) {
        if (ret == null || !ContextManager.isActive()) {
            return;
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
    }

    void handleMethodException(Throwable t) {
        if (ContextManager.isActive()) {
            ContextManager.activeSpan().log(t);
        }
    }
}
