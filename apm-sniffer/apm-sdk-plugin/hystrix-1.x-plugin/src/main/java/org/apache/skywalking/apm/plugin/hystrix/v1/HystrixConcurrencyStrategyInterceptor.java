/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 *
 */

package org.apache.skywalking.apm.plugin.hystrix.v1;

import com.netflix.hystrix.strategy.HystrixPlugins;
import com.netflix.hystrix.strategy.concurrency.HystrixConcurrencyStrategy;
import java.lang.reflect.Method;

import com.netflix.hystrix.strategy.eventnotifier.HystrixEventNotifier;
import com.netflix.hystrix.strategy.executionhook.HystrixCommandExecutionHook;
import com.netflix.hystrix.strategy.metrics.HystrixMetricsPublisher;
import com.netflix.hystrix.strategy.properties.HystrixPropertiesStrategy;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;

public class HystrixConcurrencyStrategyInterceptor implements InstanceMethodsAroundInterceptor {
    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
        MethodInterceptResult result) throws Throwable {

    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
        Object ret) throws Throwable {
        SWHystrixPluginsWrapperCache wrapperCache = (SWHystrixPluginsWrapperCache) objInst.getSkyWalkingDynamicField();
        if (wrapperCache == null || wrapperCache.getSwHystrixConcurrencyStrategyWrapper() == null) {
            synchronized (objInst) {
                if (wrapperCache == null) {
                    wrapperCache = new SWHystrixPluginsWrapperCache();
                    objInst.setSkyWalkingDynamicField(wrapperCache);
                }
                if (wrapperCache.getSwHystrixConcurrencyStrategyWrapper() == null) {
                    // Return and register wrapper only for the first time
                    // Try to believe that all other strategies will use the their delegates
                    SWHystrixConcurrencyStrategyWrapper wrapper = new SWHystrixConcurrencyStrategyWrapper((HystrixConcurrencyStrategy) ret);
                    wrapperCache.setSwHystrixConcurrencyStrategyWrapper(wrapper);

                    registerSWHystrixConcurrencyStrategyWrapper(wrapper);

                    return wrapper;
                }
            }
        }
        return ret;
    }

    private void registerSWHystrixConcurrencyStrategyWrapper(SWHystrixConcurrencyStrategyWrapper wrapper) {
        // Copy from Spring Cloud Sleuth
        HystrixCommandExecutionHook commandExecutionHook = HystrixPlugins.getInstance().getCommandExecutionHook();
        HystrixEventNotifier eventNotifier = HystrixPlugins.getInstance().getEventNotifier();
        HystrixMetricsPublisher metricsPublisher = HystrixPlugins.getInstance().getMetricsPublisher();
        HystrixPropertiesStrategy propertiesStrategy = HystrixPlugins.getInstance().getPropertiesStrategy();
        HystrixPlugins.reset();
        HystrixPlugins.getInstance().registerConcurrencyStrategy(wrapper);
        HystrixPlugins.getInstance().registerCommandExecutionHook(commandExecutionHook);
        HystrixPlugins.getInstance().registerEventNotifier(eventNotifier);
        HystrixPlugins.getInstance().registerMetricsPublisher(metricsPublisher);
        HystrixPlugins.getInstance().registerPropertiesStrategy(propertiesStrategy);
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
        Class<?>[] argumentsTypes, Throwable t) {

    }
}
