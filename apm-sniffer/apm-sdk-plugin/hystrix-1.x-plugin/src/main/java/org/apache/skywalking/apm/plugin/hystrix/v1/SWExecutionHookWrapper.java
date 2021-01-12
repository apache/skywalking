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

import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixInvokable;
import com.netflix.hystrix.exception.HystrixRuntimeException;
import com.netflix.hystrix.strategy.executionhook.HystrixCommandExecutionHook;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;

/**
 * {@link SWExecutionHookWrapper} wrapper the HystrixCommandExecutionHook object for tracing.
 */
public class SWExecutionHookWrapper extends HystrixCommandExecutionHook {
    private final HystrixCommandExecutionHook actual;

    private static ILog LOGGER = LogManager.getLogger(SWExecutionHookWrapper.class);

    public SWExecutionHookWrapper(HystrixCommandExecutionHook actual) {
        this.actual = actual;
    }

    @Override
    public <T> void onStart(HystrixInvokable<T> commandInstance) {
        if (!(commandInstance instanceof EnhancedInstance)) {
            actual.onStart(commandInstance);
            return;
        }

        try {
            EnhancedInstance enhancedInstance = (EnhancedInstance) commandInstance;
            EnhanceRequireObjectCache enhanceRequireObjectCache = (EnhanceRequireObjectCache) enhancedInstance.getSkyWalkingDynamicField();
            if (ContextManager.isActive()) {
                enhanceRequireObjectCache.setContextSnapshot(ContextManager.capture());
            }
        } catch (Exception e) {
            LOGGER.error("Failed to set ContextSnapshot.", e);
        }
        actual.onStart(commandInstance);
    }

    @Override
    public <T> void onExecutionStart(HystrixInvokable<T> commandInstance) {
        actual.onExecutionStart(commandInstance);
    }

    @Override
    public <T> Exception onExecutionError(HystrixInvokable<T> commandInstance, Exception e) {
        return actual.onExecutionError(commandInstance, e);
    }

    @Override
    public <T> void onExecutionSuccess(HystrixInvokable<T> commandInstance) {
        actual.onExecutionSuccess(commandInstance);
    }

    @Override
    public <T> void onFallbackStart(HystrixInvokable<T> commandInstance) {
        actual.onFallbackStart(commandInstance);
    }

    @Override
    public <T> Exception onFallbackError(HystrixInvokable<T> commandInstance, Exception e) {
        return actual.onFallbackError(commandInstance, e);
    }

    @Override
    public <T> void onFallbackSuccess(HystrixInvokable<T> commandInstance) {
        actual.onFallbackSuccess(commandInstance);
    }

    @Override
    public <T> Exception onRunError(HystrixInvokable<T> commandInstance, Exception e) {
        return actual.onRunError(commandInstance, e);
    }

    @Override
    public <T> Exception onRunError(HystrixCommand<T> commandInstance, Exception e) {
        return actual.onRunError(commandInstance, e);
    }

    @Override
    public <T> Exception onError(HystrixInvokable<T> commandInstance, HystrixRuntimeException.FailureType failureType,
        Exception e) {
        return actual.onError(commandInstance, failureType, e);
    }

    @Override
    public <T> void onSuccess(HystrixInvokable<T> commandInstance) {
        actual.onSuccess(commandInstance);
    }

    @Override
    public <T> T onEmit(HystrixInvokable<T> commandInstance, T value) {
        return actual.onEmit(commandInstance, value);
    }

    @Override
    public <T> T onExecutionEmit(HystrixInvokable<T> commandInstance, T value) {
        return actual.onExecutionEmit(commandInstance, value);
    }

    @Override
    public <T> T onFallbackEmit(HystrixInvokable<T> commandInstance, T value) {
        return actual.onFallbackEmit(commandInstance, value);
    }

    @Override
    public <T> void onCacheHit(HystrixInvokable<T> commandInstance) {
        actual.onCacheHit(commandInstance);
    }

    @Override
    public <T> void onThreadComplete(HystrixInvokable<T> commandInstance) {
        actual.onThreadComplete(commandInstance);
    }

    @Override
    public <T> void onThreadStart(HystrixInvokable<T> commandInstance) {
        actual.onThreadStart(commandInstance);
    }

    @Override
    public <T> Exception onError(HystrixCommand<T> commandInstance, HystrixRuntimeException.FailureType failureType,
        Exception e) {
        return actual.onError(commandInstance, failureType, e);
    }

    @Override
    public <T> Exception onFallbackError(HystrixCommand<T> commandInstance, Exception e) {
        return actual.onFallbackError(commandInstance, e);
    }

    @Override
    public <T> T onComplete(HystrixCommand<T> commandInstance, T response) {
        return actual.onComplete(commandInstance, response);
    }

    @Override
    public <T> T onComplete(HystrixInvokable<T> commandInstance, T response) {
        return actual.onComplete(commandInstance, response);
    }

    @Override
    public <T> T onFallbackSuccess(HystrixCommand<T> commandInstance, T fallbackResponse) {
        return actual.onFallbackSuccess(commandInstance, fallbackResponse);
    }

    @Override
    public <T> T onFallbackSuccess(HystrixInvokable<T> commandInstance, T fallbackResponse) {
        return actual.onFallbackSuccess(commandInstance, fallbackResponse);
    }

    @Override
    public <T> T onRunSuccess(HystrixCommand<T> commandInstance, T response) {
        return actual.onRunSuccess(commandInstance, response);
    }

    @Override
    public <T> T onRunSuccess(HystrixInvokable<T> commandInstance, T response) {
        return actual.onRunSuccess(commandInstance, response);
    }

    @Override
    public <T> void onFallbackStart(HystrixCommand<T> commandInstance) {
        actual.onFallbackStart(commandInstance);
    }

    @Override
    public <T> void onRunStart(HystrixCommand<T> commandInstance) {
        actual.onRunStart(commandInstance);
    }

    @Override
    public <T> void onRunStart(HystrixInvokable<T> commandInstance) {
        actual.onRunStart(commandInstance);
    }

    @Override
    public <T> void onStart(HystrixCommand<T> commandInstance) {
        if (!(commandInstance instanceof EnhancedInstance)) {
            actual.onStart(commandInstance);
            return;
        }

        try {
            EnhancedInstance enhancedInstance = (EnhancedInstance) commandInstance;
            EnhanceRequireObjectCache enhanceRequireObjectCache = (EnhanceRequireObjectCache) enhancedInstance.getSkyWalkingDynamicField();
            if (ContextManager.isActive()) {
                enhanceRequireObjectCache.setContextSnapshot(ContextManager.capture());
            }
        } catch (Exception e) {
            LOGGER.error("Failed to set ContextSnapshot.", e);
        }
        actual.onStart(commandInstance);
    }

    @Override
    public <T> void onThreadComplete(HystrixCommand<T> commandInstance) {
        actual.onThreadComplete(commandInstance);
    }

    @Override
    public <T> void onThreadStart(HystrixCommand<T> commandInstance) {
        actual.onThreadStart(commandInstance);
    }
}
