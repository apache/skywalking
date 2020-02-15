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

import com.netflix.hystrix.strategy.HystrixPlugins;
import com.netflix.hystrix.strategy.concurrency.HystrixConcurrencyStrategy;
import com.netflix.hystrix.strategy.eventnotifier.HystrixEventNotifier;
import com.netflix.hystrix.strategy.executionhook.HystrixCommandExecutionHook;
import java.lang.reflect.Method;

import com.netflix.hystrix.strategy.metrics.HystrixMetricsPublisher;
import com.netflix.hystrix.strategy.properties.HystrixPropertiesStrategy;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;

/**
 * {@link HystrixPluginsInterceptor} wrapper the {@link HystrixCommandExecutionHook} object by using {@link
 * SWExecutionHookWrapper} when the {@link HystrixPlugins#getCommandExecutionHook()} method invoked.
 */
public class HystrixPluginsInterceptor implements InstanceMethodsAroundInterceptor {

    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
        MethodInterceptResult result) throws Throwable {
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
        Object ret) throws Throwable {
        SWHystrixPluginsWrapperCache wrapperCache = (SWHystrixPluginsWrapperCache) objInst.getSkyWalkingDynamicField();
        if (wrapperCache == null || wrapperCache.getSwExecutionHookWrapper() == null) {
            synchronized (objInst) {
                if (wrapperCache == null) {
                    wrapperCache = new SWHystrixPluginsWrapperCache();
                    objInst.setSkyWalkingDynamicField(wrapperCache);
                }
                if (wrapperCache.getSwExecutionHookWrapper() == null) {
                    // Return and register wrapper only for the first time
                    // Try to believe that all other hooks will use the their delegates
                    SWExecutionHookWrapper wrapper = new SWExecutionHookWrapper((HystrixCommandExecutionHook) ret);
                    wrapperCache.setSwExecutionHookWrapper(wrapper);

                    registerSWExecutionHookWrapper(wrapper);

                    return wrapper;
                }
            }
        }

        return ret;
    }

    private static void registerSWExecutionHookWrapper(SWExecutionHookWrapper wrapper) {
        HystrixConcurrencyStrategy concurrencyStrategy = HystrixPlugins.getInstance().getConcurrencyStrategy();
        HystrixEventNotifier eventNotifier = HystrixPlugins.getInstance().getEventNotifier();
        HystrixMetricsPublisher metricsPublisher = HystrixPlugins.getInstance().getMetricsPublisher();
        HystrixPropertiesStrategy propertiesStrategy = HystrixPlugins.getInstance().getPropertiesStrategy();
        HystrixPlugins.reset();
        HystrixPlugins.getInstance().registerConcurrencyStrategy(concurrencyStrategy);
        HystrixPlugins.getInstance().registerCommandExecutionHook(wrapper);
        HystrixPlugins.getInstance().registerEventNotifier(eventNotifier);
        HystrixPlugins.getInstance().registerMetricsPublisher(metricsPublisher);
        HystrixPlugins.getInstance().registerPropertiesStrategy(propertiesStrategy);
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
        Class<?>[] argumentsTypes, Throwable t) {

    }
}
