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
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;

import java.lang.reflect.Method;

/**
 * implements Callback and EnhancedInstance, for kafka callback in lambda expression
 */
public class CallbackAdapterInterceptor implements Callback, EnhancedInstance {

    private Method method;

    private CallbackCache callbackCache;

    private static CallbackInterceptor CALLBACK_INTERCEPTOR = new CallbackInterceptor();

    private ILog logger = LogManager.getLogger(CallbackAdapterInterceptor.class);

    public CallbackAdapterInterceptor(CallbackCache callbackCache) {

        this.callbackCache = callbackCache;
        try {
            this.method = CallbackAdapterInterceptor.class.getMethod("onCompletion", new Class[]{RecordMetadata.class, Exception.class});
        } catch (Exception e) {
        }
    }

    @Override
    public void onCompletion(RecordMetadata metadata, Exception exception) {
        MethodInterceptResult result = new MethodInterceptResult();
        Object[] allArguments = new Object[]{metadata, exception};
        try {
            CALLBACK_INTERCEPTOR.beforeMethod(this, method, allArguments, method.getParameterTypes(), result);
        } catch (Throwable t) {
            logger.error(t, "class[{}] before method[{}] intercept failure", this.getClass(), method.getName());
        }

        Object ret = null;
        try {
            if (!result.isContinue()) {
                ret = result._ret();
            } else {
                callbackCache.getCallback().onCompletion(metadata, exception);
            }
        } catch (Throwable t) {
            try {
                CALLBACK_INTERCEPTOR.handleMethodException(this, method, allArguments, method.getParameterTypes(), t);
            } catch (Throwable t2) {
                logger.error(t2, "class[{}] handle method[{}] exception failure", this.getClass(), method.getName());
            }
            throw t;
        } finally {
            try {
                CALLBACK_INTERCEPTOR.afterMethod(this, method, allArguments, method.getParameterTypes(), ret);
            } catch (Throwable t) {
                logger.error(t, "class[{}] after method[{}] intercept failure", this.getClass(), method.getName());
            }
        }
    }

    @Override
    public Object getSkyWalkingDynamicField() {
        return callbackCache;
    }

    @Override
    public void setSkyWalkingDynamicField(Object value) {
        return;
    }
}