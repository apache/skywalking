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

package org.apache.skywalking.apm.plugin.spring.cloud.gateways.v2;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.AbstractServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * @author zhaoyuguang
 */
public class NettyRoutingFilterInterceptor implements InstanceMethodsAroundInterceptor {

    private Logger logger = LoggerFactory.getLogger(NettyRoutingFilterInterceptor.class);

    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
                             MethodInterceptResult result) throws Throwable {
        logger.info("NettyRoutingFilterInterceptor::filter" + Thread.currentThread().getId());
        ServerWebExchange exchange = (ServerWebExchange) allArguments[0];
        URI requestUrl = exchange.getRequiredAttribute(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR);
        String scheme = requestUrl.getScheme();
        if (!ServerWebExchangeUtils.isAlreadyRouted(exchange) && ("http".equals(scheme) || "https".equals(scheme))) {
            AbstractServerHttpRequest request = (AbstractServerHttpRequest) exchange.getRequest();
            HttpHeaders before = request.getHeaders();
            Map<String, List<String>> after = new HashMap<String, List<String>>();
            for (String k : before.keySet()) {
                after.put(k, before.get(k));
            }

            ContextCarrier contextCarrier = new ContextCarrier();
            AbstractSpan span = ContextManager.createExitSpan("send", contextCarrier, requestUrl.getAuthority());

            span.setComponent(ComponentsDefine.SPRING_CLOUD_GATEWAYS);
            Tags.URL.set(span, requestUrl.toString());
            Tags.HTTP.METHOD.set(span, requestUrl.getPath());
            SpanLayer.asHttp(span);

            CarrierItem next = contextCarrier.items();
            while (next.hasNext()) {
                next = next.next();
                List<String> v = new ArrayList<String>();
                v.add(next.getHeadValue());
                after.put(next.getHeadKey(), v);
//                try {
//                    before.put("c", v);
//                } catch (Exception e) {
//                    logger.error("e", e);
//                }
            }
            Field field = AbstractServerHttpRequest.class.getDeclaredField("headers");
            field.setAccessible(true);
            HttpHeaders headers = new HttpHeaders();
            headers.putAll(after);
            field.set(request, headers);
        }
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments,
                              Class<?>[] argumentsTypes, Object ret) throws Throwable {
        return ret;
    }


    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
                                      Class<?>[] argumentsTypes, Throwable t) {
    }
}
