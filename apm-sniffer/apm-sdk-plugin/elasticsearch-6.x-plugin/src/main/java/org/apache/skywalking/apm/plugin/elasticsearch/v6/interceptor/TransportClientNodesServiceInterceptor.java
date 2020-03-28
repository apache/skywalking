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

package org.apache.skywalking.apm.plugin.elasticsearch.v6.interceptor;

import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceConstructorInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.plugin.elasticsearch.v6.TransportClientEnhanceInfo;
import org.apache.skywalking.apm.plugin.elasticsearch.v6.TransportAddressCache;
import org.elasticsearch.action.support.AdapterActionFuture;
import org.elasticsearch.common.transport.TransportAddress;

import java.lang.reflect.Method;

public class TransportClientNodesServiceInterceptor implements InstanceConstructorInterceptor {

    @Override
    public void onConstruct(EnhancedInstance objInst, Object[] allArguments) {

        EnhancedInstance actions = (EnhancedInstance) allArguments[1];
        objInst.setSkyWalkingDynamicField(actions.getSkyWalkingDynamicField());
    }

    public static class AddTransportAddressesInterceptor implements InstanceMethodsAroundInterceptor {
        @Override
        public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
                                 MethodInterceptResult result) throws Throwable {

        }

        @Override
        public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
                                  Object ret) throws Throwable {
            TransportClientEnhanceInfo transportClientEnhanceInfo = (TransportClientEnhanceInfo) objInst.getSkyWalkingDynamicField();
            TransportAddressCache transportAddressCache = transportClientEnhanceInfo.getTransportAddressCache();
            if (transportAddressCache == null) {
                transportAddressCache = new TransportAddressCache();
                transportClientEnhanceInfo.setTransportAddressCache(transportAddressCache);
            }
            transportAddressCache.addDiscoveryNode((TransportAddress[]) allArguments[0]);
            return ret;
        }

        @Override
        public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
                                          Class<?>[] argumentsTypes, Throwable t) {

        }
    }

    public static class RemoveTransportAddressInterceptor implements InstanceMethodsAroundInterceptor {
        @Override
        public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
                                 MethodInterceptResult result) throws Throwable {

        }

        @Override
        public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
                                  Object ret) throws Throwable {
            TransportClientEnhanceInfo transportClientEnhanceInfo = (TransportClientEnhanceInfo) objInst.getSkyWalkingDynamicField();
            TransportAddressCache transportAddressCache = transportClientEnhanceInfo.getTransportAddressCache();
            if (transportAddressCache == null) {
                transportAddressCache = new TransportAddressCache();
                transportClientEnhanceInfo.setTransportAddressCache(transportAddressCache);
            }
            transportAddressCache.removeDiscoveryNode((TransportAddress) allArguments[0]);
            return ret;
        }

        @Override
        public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
                                          Class<?>[] argumentsTypes, Throwable t) {

        }
    }

    public static class ExecuteInterceptor implements InstanceMethodsAroundInterceptor {

        @Override
        public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes, MethodInterceptResult result) throws Throwable {

            // tracking AdapterActionFuture.actionGet
            if (allArguments.length >= 2 && allArguments[1] instanceof AdapterActionFuture) {
                AdapterActionFuture actionFuture = (AdapterActionFuture) allArguments[1];
                ((EnhancedInstance) actionFuture).setSkyWalkingDynamicField(true);
            }
        }

        @Override
        public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes, Object ret) throws Throwable {
            return null;
        }

        @Override
        public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes, Throwable t) {

        }
    }
}
