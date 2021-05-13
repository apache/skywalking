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

package org.apache.skywalking.apm.plugin.zookeeper;

import org.apache.jute.Record;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceConstructorInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;
import org.apache.zookeeper.client.StaticHostProvider;
import org.apache.zookeeper.proto.RequestHeader;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ClientCnxnInterceptor implements InstanceMethodsAroundInterceptor, InstanceConstructorInterceptor {

    private static final ILog LOGGER = LogManager.getLogger(ClientCnxnInterceptor.class);

    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
        MethodInterceptResult result) throws Throwable {
        String peer = (String) objInst.getSkyWalkingDynamicField();
        RequestHeader header = (RequestHeader) allArguments[0];
        String operationName = ZooOpt.getOperationName(header.getType());
        AbstractSpan span = ContextManager.createExitSpan("Zookeeper/" + operationName, peer);
        span.setComponent(ComponentsDefine.ZOOKEEPER);
        Tags.DB_TYPE.set(span, "Zookeeper");
        ZooOpt.setTags(span, (Record) allArguments[2]);
        SpanLayer.asCache(span);
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
        AbstractSpan span = ContextManager.activeSpan();
        span.log(t);
    }

    @Override
    public void onConstruct(EnhancedInstance objInst, Object[] allArguments) {
        StaticHostProvider hostProvider = (StaticHostProvider) allArguments[1];
        try {
            Field field = StaticHostProvider.class.getDeclaredField("serverAddresses");
            field.setAccessible(true);
            @SuppressWarnings("unchecked") List<InetSocketAddress> serverAddresses = (List<InetSocketAddress>) field.get(hostProvider);
            List<String> addresses = new ArrayList<String>();
            for (InetSocketAddress address : serverAddresses) {
                addresses.add(address.getHostName() + ":" + address.getPort());
            }
            Collections.sort(addresses);
            StringBuilder peer = new StringBuilder();
            for (String address : addresses) {
                peer.append(address).append(";");
            }
            objInst.setSkyWalkingDynamicField(peer.toString());
        } catch (NoSuchFieldException e) {
            LOGGER.warn("NoSuchFieldException, not be compatible with this version of zookeeper", e);
        } catch (IllegalAccessException e) {
            LOGGER.warn("IllegalAccessException, not be compatible with this version of zookeeper", e);
        }
    }
}
