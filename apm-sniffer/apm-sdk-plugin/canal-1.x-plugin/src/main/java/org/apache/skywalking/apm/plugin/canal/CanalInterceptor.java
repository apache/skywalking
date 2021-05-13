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

package org.apache.skywalking.apm.plugin.canal;

import com.alibaba.otter.canal.client.impl.SimpleCanalConnector;
import java.util.Objects;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;

import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.util.List;

public class CanalInterceptor implements InstanceMethodsAroundInterceptor {
    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
        MethodInterceptResult result) throws Throwable {
        CanalEnhanceInfo canalEnhanceInfo = (CanalEnhanceInfo) objInst.getSkyWalkingDynamicField();
        SimpleCanalConnector connector = (SimpleCanalConnector) objInst;

        String url = canalEnhanceInfo.getUrl();
        if (Objects.equals(url, "") || url == null) {
            InetSocketAddress address = (InetSocketAddress) connector.getNextAddress();
            String runningAddress = address.getAddress().toString() + ":" + address.getPort();
            runningAddress = runningAddress.replace('/', ' ');
            url = runningAddress;
            List<InetSocketAddress> socketAddressList = (List<InetSocketAddress>) ContextManager.getRuntimeContext()
                                                                                                .get("currentAddress");
            if (socketAddressList != null && socketAddressList.size() > 0) {
                for (InetSocketAddress socketAddress : socketAddressList) {
                    String currentAddress = socketAddress.getAddress().toString() + ":" + socketAddress.getPort();
                    currentAddress = currentAddress.replace('/', ' ');
                    if (!currentAddress.equals(runningAddress)) {
                        url = url + "," + currentAddress;
                    }
                }
            }
        }
        String batchSize = allArguments[0].toString();
        String destination = canalEnhanceInfo.getDestination();
        AbstractSpan activeSpan = ContextManager.createExitSpan("Canal/" + destination, url)
                                                .start(System.currentTimeMillis());
        activeSpan.setComponent(ComponentsDefine.CANAL);
        activeSpan.tag(Tags.ofKey("batchSize"), batchSize);
        activeSpan.tag(Tags.ofKey("destination"), destination);

    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
        Object ret) throws Throwable {
        ContextManager.stopSpan();
        return ret;

    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
        Class<?>[] argumentsTypes, Throwable t) {
        ContextManager.activeSpan().log(t);
    }
}