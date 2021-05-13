/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.apache.skywalking.apm.plugin.spring.cloud.gateway.v3x;

import io.netty.handler.codec.http.HttpResponseStatus;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.plugin.spring.cloud.gateway.v3x.define.EnhanceObjectCache;
import org.reactivestreams.Publisher;
import reactor.netty.Connection;
import reactor.netty.http.client.HttpClientResponse;

import java.lang.reflect.Method;
import java.util.function.BiFunction;

/**
 * This class intercept <code>responseConnection</code> method.
 * <p>
 * After downstream service response, finish the span in the {@link EnhanceObjectCache}.
 */
public class HttpClientFinalizerResponseConnectionInterceptor implements InstanceMethodsAroundInterceptor {

    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
            MethodInterceptResult result) throws Throwable {
        BiFunction<? super HttpClientResponse, ? super Connection, ? extends Publisher> finalReceiver = (BiFunction<? super HttpClientResponse, ? super Connection, ? extends Publisher>) allArguments[0];
        EnhanceObjectCache cache = (EnhanceObjectCache) objInst.getSkyWalkingDynamicField();
        allArguments[0] = (BiFunction<HttpClientResponse, Connection, Publisher>) (response, connection) -> {
            Publisher publisher = finalReceiver.apply(response, connection);
            if (cache == null) {
                return publisher;
            }
            // receive the response. Stop the span.
            if (cache.getSpan() != null) {
                if (response.status().code() >= HttpResponseStatus.BAD_REQUEST.code()) {
                    cache.getSpan().errorOccurred();
                }
                Tags.STATUS_CODE.set(cache.getSpan(), String.valueOf(response.status().code()));
                cache.getSpan().asyncFinish();
            }

            if (cache.getSpan1() != null) {
                cache.getSpan1().asyncFinish();
            }
            return publisher;
        };
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
            Object ret) throws Throwable {
        return ret;
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
            Class<?>[] argumentsTypes, Throwable t) {

    }
}
