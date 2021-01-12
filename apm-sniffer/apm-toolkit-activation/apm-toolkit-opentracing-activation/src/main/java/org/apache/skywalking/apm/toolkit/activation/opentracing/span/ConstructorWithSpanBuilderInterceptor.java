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

package org.apache.skywalking.apm.toolkit.activation.opentracing.span;

import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceConstructorInterceptor;
import org.apache.skywalking.apm.toolkit.opentracing.SkywalkingSpanBuilder;
import org.apache.skywalking.apm.toolkit.opentracing.Tag;
import org.apache.skywalking.apm.util.StringUtil;

public class ConstructorWithSpanBuilderInterceptor implements InstanceConstructorInterceptor {

    @Override
    public void onConstruct(EnhancedInstance objInst, Object[] allArguments) {
        SkywalkingSpanBuilder spanBuilder = (SkywalkingSpanBuilder) allArguments[0];

        AbstractSpan span;
        if (spanBuilder.isEntry()) {
            span = ContextManager.createEntrySpan(spanBuilder.getOperationName(), null);
        } else if (spanBuilder.isExit() && (!StringUtil.isEmpty(spanBuilder.getPeer()))) {
            span = ContextManager.createExitSpan(spanBuilder.getOperationName(), buildRemotePeer(spanBuilder));
        } else {
            span = ContextManager.createLocalSpan(spanBuilder.getOperationName());
        }

        for (Tag tag : spanBuilder.getTags()) {
            span.tag(Tags.ofKey(tag.getKey()), tag.getValue());
        }
        if (spanBuilder.isError()) {
            span.errorOccurred();
        }

        objInst.setSkyWalkingDynamicField(span);
    }

    private String buildRemotePeer(SkywalkingSpanBuilder spanBuilder) {
        return spanBuilder.getPeer() + (spanBuilder.getPort() == 0 ? "" : ":" + spanBuilder.getPort());
    }
}
