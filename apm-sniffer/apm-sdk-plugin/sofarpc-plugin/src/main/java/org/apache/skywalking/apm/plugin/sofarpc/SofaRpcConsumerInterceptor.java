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

package org.apache.skywalking.apm.plugin.sofarpc;

import com.alipay.sofa.rpc.client.ProviderInfo;
import com.alipay.sofa.rpc.context.RpcInternalContext;
import com.alipay.sofa.rpc.core.request.SofaRequest;
import com.alipay.sofa.rpc.core.response.SofaResponse;
import java.lang.reflect.Method;
import org.apache.skywalking.apm.agent.core.context.CarrierItem;
import org.apache.skywalking.apm.agent.core.context.ContextCarrier;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;

public class SofaRpcConsumerInterceptor implements InstanceMethodsAroundInterceptor {

    public static final String SKYWALKING_PREFIX = "skywalking.";

    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
                             MethodInterceptResult result) throws Throwable {
        SofaRequest sofaRequest = (SofaRequest) allArguments[0];
        RpcInternalContext rpcContext = RpcInternalContext.getContext();

        ProviderInfo providerInfo = rpcContext.getProviderInfo();

        final String host = providerInfo.getHost();
        final int port = providerInfo.getPort();
        final ContextCarrier contextCarrier = new ContextCarrier();
        final String operationName = generateOperationName(providerInfo, sofaRequest);
        AbstractSpan span = ContextManager.createExitSpan(operationName, contextCarrier, host + ":" + port);
        CarrierItem next = contextCarrier.items();
        while (next.hasNext()) {
            next = next.next();
            String key = next.getHeadKey();
            String skyWalkingKey = SKYWALKING_PREFIX + key;
            sofaRequest.addRequestProp(skyWalkingKey, next.getHeadValue());
        }

        Tags.URL.set(span, generateRequestURL(providerInfo, sofaRequest));
        span.setComponent(ComponentsDefine.SOFARPC);
        SpanLayer.asRPCFramework(span);
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
                              Object ret) throws Throwable {
        SofaResponse result = (SofaResponse) ret;
        if (result != null && result.isError()) {
            dealException((Throwable) result.getAppResponse());
        }

        ContextManager.stopSpan();
        return ret;
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
                                      Class<?>[] argumentsTypes, Throwable t) {
        dealException(t);
    }

    /**
     * Log the throwable, which occurs in Dubbo RPC service.
     */
    private void dealException(Throwable throwable) {
        AbstractSpan span = ContextManager.activeSpan();
        span.log(throwable);
    }

    /**
     * Format operation name. e.g. org.apache.skywalking.apm.plugin.test.Test.test(String)
     *
     * @return operation name.
     */
    private String generateOperationName(ProviderInfo providerInfo, SofaRequest sofaRequest) {
        StringBuilder operationName = new StringBuilder();
        operationName.append(sofaRequest.getInterfaceName());
        operationName.append(".").append(sofaRequest.getMethodName()).append("(");
        for (String arg : sofaRequest.getMethodArgSigs()) {
            operationName.append(arg).append(",");
        }

        if (sofaRequest.getMethodArgs().length > 0) {
            operationName.delete(operationName.length() - 1, operationName.length());
        }

        operationName.append(")");

        return operationName.toString();
    }

    /**
     * Format request url. e.g. bolt://127.0.0.1:20880/org.apache.skywalking.apm.plugin.test.Test.test(String).
     *
     * @return request url.
     */
    private String generateRequestURL(ProviderInfo providerInfo, SofaRequest sofaRequest) {
        StringBuilder requestURL = new StringBuilder();
        requestURL.append(providerInfo.getProtocolType()).append("://");
        requestURL.append(providerInfo.getHost());
        requestURL.append(":").append(providerInfo.getPort()).append("/");
        requestURL.append(generateOperationName(providerInfo, sofaRequest));
        return requestURL.toString();
    }
}
