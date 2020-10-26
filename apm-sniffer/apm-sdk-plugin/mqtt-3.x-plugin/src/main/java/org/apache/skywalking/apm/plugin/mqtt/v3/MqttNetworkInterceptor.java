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

package org.apache.skywalking.apm.plugin.mqtt.v3;

import java.lang.reflect.Method;
import java.util.Arrays;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.util.StringUtil;
import org.eclipse.paho.client.mqttv3.internal.NetworkModule;

public class MqttNetworkInterceptor implements InstanceMethodsAroundInterceptor {

    @Override
    public void beforeMethod(EnhancedInstance enhancedInstance, Method method, Object[] allArguments,
                             Class<?>[] classes, MethodInterceptResult methodInterceptResult) throws Throwable {
        NetworkModule[] networkModules = (NetworkModule[]) allArguments[0];
        MqttEnhanceRequiredInfo requiredInfo = new MqttEnhanceRequiredInfo();
        String[] serverURIs = Arrays.stream(networkModules)
                                    .map(networkModule -> networkModule.getServerURI())
                                    .toArray(String[]::new);
        requiredInfo.setBrokerServers(StringUtil.join(';', serverURIs));
        requiredInfo.setStartTime(System.currentTimeMillis());
        enhancedInstance.setSkyWalkingDynamicField(requiredInfo);

    }

    @Override
    public Object afterMethod(EnhancedInstance enhancedInstance, Method method, Object[] objects, Class<?>[] classes,
                              Object o) throws Throwable {
        return o;
    }

    @Override
    public void handleMethodException(EnhancedInstance enhancedInstance, Method method, Object[] objects,
                                      Class<?>[] classes, Throwable throwable) {
        ContextManager.activeSpan().log(throwable);
    }
}
