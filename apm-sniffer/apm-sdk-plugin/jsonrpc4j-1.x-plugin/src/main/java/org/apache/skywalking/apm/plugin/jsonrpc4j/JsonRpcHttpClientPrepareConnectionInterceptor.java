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

package org.apache.skywalking.apm.plugin.jsonrpc4j;

import org.apache.skywalking.apm.agent.core.context.CarrierItem;
import org.apache.skywalking.apm.agent.core.context.ContextCarrier;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;

import java.lang.reflect.Method;
import java.net.HttpURLConnection;

@SuppressWarnings("unused")
public class JsonRpcHttpClientPrepareConnectionInterceptor implements InstanceMethodsAroundInterceptor {
    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] objects, Class<?>[] classes, MethodInterceptResult result) {

    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] objects, Class<?>[] classes, Object retObj) {
        HttpURLConnection connection = (HttpURLConnection) retObj;
        AbstractSpan span = ContextManager.activeSpan();
        if (span == null) {
            return retObj;
        }

        ContextCarrier carrier = new ContextCarrier();
        ContextManager.inject(carrier);
        CarrierItem item = carrier.items();
        if (item.hasNext()) {
            item = item.next();
            connection.setRequestProperty(item.getHeadKey(), item.getHeadValue());
        }

        return retObj;
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] objects, Class<?>[] classes, Throwable throwable) {

    }
}
