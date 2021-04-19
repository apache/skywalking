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

import org.apache.skywalking.apm.agent.core.context.ContextCarrier;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceConstructorInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;

import java.lang.reflect.Method;
import java.net.URL;

@SuppressWarnings("unused")
public class JsonRpcHttpClientInterceptor implements InstanceMethodsAroundInterceptor, InstanceConstructorInterceptor {

    @Override
    public void onConstruct(EnhancedInstance objInst, Object[] objects) {
        URL url = (URL) objects[1];
        JsonRpcPeerInfo clientDto = new JsonRpcPeerInfo();
        int port = url.getPort();
        if (port < 0) {
            if (isHttps(url)) {
                port = 443;
            } else {
                port = 80;
            }
        }

        clientDto.setPort(port);
        clientDto.setServiceUrl(url);
        objInst.setSkyWalkingDynamicField(clientDto);
    }

    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] objects, Class<?>[] classes, MethodInterceptResult result) throws Throwable {
        JsonRpcPeerInfo clientDto = (JsonRpcPeerInfo) objInst.getSkyWalkingDynamicField();
        String methodName = objects[0].toString();
        String operationName = clientDto.getServiceUrl().getPath() + "." + methodName;
        AbstractSpan span = ContextManager.createExitSpan(operationName, new ContextCarrier(), clientDto.getServiceUrl().getHost() + ":" + clientDto.getPort());
        span.setComponent(ComponentsDefine.JSON_RPC);
        Tags.HTTP.METHOD.set(span, "POST");
        Tags.URL.set(span, clientDto.getServiceUrlString());
        SpanLayer.asRPCFramework(span);
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] objects, Class<?>[] classes, Object o) throws Throwable {
        ContextManager.stopSpan();
        return o;
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] objects, Class<?>[] classes, Throwable throwable) {
        ContextManager.activeSpan().log(throwable);
    }

    private boolean isHttps(URL url) {
        return url.getProtocol().equals("https");
    }
}
