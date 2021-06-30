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

package org.apache.skywalking.apm.plugin.sentinel.v1;

import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.authority.AuthorityException;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeException;
import com.alibaba.csp.sentinel.slots.block.flow.FlowException;
import com.alibaba.csp.sentinel.slots.system.SystemBlockException;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import static org.apache.skywalking.apm.plugin.sentinel.v1.Constants.SENTINEL_SPAN;

public class EntryMethodInterceptor implements InstanceMethodsAroundInterceptor {
    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
                             MethodInterceptResult result) throws Throwable {
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
                              Object ret) throws Throwable {
        return ret;
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
                                      Class<?>[] argumentsTypes, Throwable t) {
        Object span = ContextManager.getRuntimeContext().get(SENTINEL_SPAN);
        if (span != null) {
            if (t instanceof BlockException) {
                Map<String, String> eventMap = new HashMap<String, String>();
                eventMap.put("blocked", "true");
                if (t instanceof FlowException) {
                    eventMap.put("block_type", "flow");
                } else if (t instanceof DegradeException) {
                    eventMap.put("block_type", "degrade");
                } else if (t instanceof SystemBlockException) {
                    eventMap.put("block_type", "system");
                } else if (t instanceof AuthorityException) {
                    eventMap.put("block_type", "authority");
                }
                ((AbstractSpan) span).log(System.currentTimeMillis(), eventMap);
            } else {
                ((AbstractSpan) span).log(t);
            }
            ContextManager.getRuntimeContext().remove(SENTINEL_SPAN);
        }
    }
}
