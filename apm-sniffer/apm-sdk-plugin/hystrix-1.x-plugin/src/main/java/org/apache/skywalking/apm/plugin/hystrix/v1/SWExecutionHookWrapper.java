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

package org.apache.skywalking.apm.plugin.hystrix.v1;

import com.netflix.hystrix.HystrixInvokable;
import com.netflix.hystrix.strategy.executionhook.HystrixCommandExecutionHook;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.ContextSnapshot;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;

/**
 * {@link SWExecutionHookWrapper} wrapper the HystrixCommandExecutionHook object for tracing.
 *
 * @author zhang xin
 */
public class SWExecutionHookWrapper extends HystrixCommandExecutionHook {
    private final HystrixCommandExecutionHook actual;

    public SWExecutionHookWrapper(HystrixCommandExecutionHook actual) {
        this.actual = actual;
    }

    @Override
    public <T> void onStart(HystrixInvokable<T> commandInstance) {
        EnhancedInstance enhancedInstance = (EnhancedInstance)commandInstance;
        EnhanceRequireObjectCache enhanceRequireObjectCache = (EnhanceRequireObjectCache)enhancedInstance.getSkyWalkingDynamicField();
        enhanceRequireObjectCache.setContextSnapshot(ContextManager.capture());
        actual.onStart(commandInstance);
    }

    @Override
    public <T> void onExecutionStart(HystrixInvokable<T> commandInstance) {
        // create a local span, and continued, The `execution method` running in other thread if the
        // hystrix strategy is `THREAD`.
        EnhancedInstance enhancedInstance = (EnhancedInstance)commandInstance;
        EnhanceRequireObjectCache enhanceRequireObjectCache = (EnhanceRequireObjectCache)enhancedInstance.getSkyWalkingDynamicField();
        ContextSnapshot snapshot = enhanceRequireObjectCache.getContextSnapshot();

        AbstractSpan activeSpan = ContextManager.createLocalSpan(enhanceRequireObjectCache.getOperationNamePrefix() + "/Execution");
        activeSpan.setComponent(ComponentsDefine.HYSTRIX);
        ContextManager.continued(snapshot);
        actual.onExecutionStart(commandInstance);

        // Because of `fall back` method running in other thread. so we need capture concurrent span for tracing.
        enhanceRequireObjectCache.setContextSnapshot(ContextManager.capture());
    }

    @Override
    public <T> Exception onExecutionError(HystrixInvokable<T> commandInstance, Exception e) {
        ContextManager.activeSpan().errorOccurred().log(e);
        ContextManager.stopSpan();
        return actual.onExecutionError(commandInstance, e);
    }

    @Override
    public <T> void onExecutionSuccess(HystrixInvokable<T> commandInstance) {
        ContextManager.stopSpan();
        actual.onExecutionSuccess(commandInstance);
    }

    @Override
    public <T> void onFallbackStart(HystrixInvokable<T> commandInstance) {
        EnhancedInstance enhancedInstance = (EnhancedInstance)commandInstance;
        EnhanceRequireObjectCache enhanceRequireObjectCache = (EnhanceRequireObjectCache)enhancedInstance.getSkyWalkingDynamicField();
        ContextSnapshot snapshot = enhanceRequireObjectCache.getContextSnapshot();

        AbstractSpan activeSpan = ContextManager.createLocalSpan(enhanceRequireObjectCache.getOperationNamePrefix() + "/Fallback");
        activeSpan.setComponent(ComponentsDefine.HYSTRIX);
        ContextManager.continued(snapshot);

        actual.onFallbackStart(commandInstance);
    }

    @Override
    public <T> Exception onFallbackError(HystrixInvokable<T> commandInstance, Exception e) {
        ContextManager.activeSpan().errorOccurred().log(e);
        ContextManager.stopSpan();
        return actual.onFallbackError(commandInstance, e);
    }

    @Override
    public <T> void onFallbackSuccess(HystrixInvokable<T> commandInstance) {
        ContextManager.stopSpan();
        actual.onFallbackSuccess(commandInstance);
    }
}
