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
import com.netflix.hystrix.strategy.HystrixPlugins;
import com.netflix.hystrix.strategy.executionhook.HystrixCommandExecutionHook;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class HystrixPluginsInterceptorTest {

    private HystrixPluginsInterceptor hystrixPluginsInterceptor;

    private EnhancedInstance enhancedInstance;

    @Before
    public void setUp() {
        hystrixPluginsInterceptor = new HystrixPluginsInterceptor();
        enhancedInstance = new EnhancedInstance() {

            private SWHystrixPluginsWrapperCache cache;

            @Override
            public Object getSkyWalkingDynamicField() {
                return cache;
            }

            @Override
            public void setSkyWalkingDynamicField(Object cache) {
                this.cache = (SWHystrixPluginsWrapperCache) cache;
            }
        };
        HystrixPlugins.reset();
    }

    @Test
    public void testSWExecutionHookWrapperWillBeRegistered() throws Throwable {
        Object wrapperResult = getCommandExecutionHookByInterceptor();
        assertTrue(wrapperResult instanceof SWExecutionHookWrapper);
        assertSame(wrapperResult, HystrixPlugins.getInstance().getCommandExecutionHook());
    }

    @Test
    public void testInterceptorWithCustomHystrixCommandExecutionHook() throws Throwable {
        Object wrapperResult = getCommandExecutionHookByInterceptor();
        assertTrue(wrapperResult instanceof SWExecutionHookWrapper);
        assertSame(HystrixPlugins.getInstance().getCommandExecutionHook(), wrapperResult);

        // register custom HystrixCommandExecutionHook
        HystrixCommandExecutionHook delegate = getCommandExecutionHookByInterceptor();
        HystrixCommandExecutionHook customCommandExecutionHook = new CustomHystrixCommandExecutionHook(delegate);
        HystrixPlugins.reset();
        HystrixPlugins.getInstance().registerCommandExecutionHook(customCommandExecutionHook);

        // custom HystrixCommandExecutionHook can be consumed
        wrapperResult = getCommandExecutionHookByInterceptor();
        assertSame(customCommandExecutionHook, wrapperResult);
        assertSame(HystrixPlugins.getInstance().getCommandExecutionHook(), wrapperResult);
    }

    private HystrixCommandExecutionHook getCommandExecutionHookByInterceptor() throws Throwable {
        return (HystrixCommandExecutionHook) hystrixPluginsInterceptor.afterMethod(enhancedInstance, null, null, null, HystrixPlugins
            .getInstance()
            .getCommandExecutionHook());
    }

    static class CustomHystrixCommandExecutionHook extends HystrixCommandExecutionHook {

        private HystrixCommandExecutionHook delegate;

        public CustomHystrixCommandExecutionHook(HystrixCommandExecutionHook delegate) {
            this.delegate = delegate;
        }

        @Override
        public <T> void onStart(HystrixInvokable<T> commandInstance) {
            delegate.onStart(commandInstance);
        }
    }

}