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

package org.apache.skywalking.apm.plugin.feign.http.v9;

import feign.RequestTemplate;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;

import java.lang.reflect.Method;
import java.util.Map;

/**
 * {@link PathVarInterceptor} intercept the Feign RequestTemplate args resolve ;
 *
 * @author qiyang
 */
public class PathVarInterceptor implements InstanceMethodsAroundInterceptor {

    static final ThreadLocal<String> URL_CONTEXT = new ThreadLocal<String>();

    /**
     * Get the {@link RequestTemplate#url()} before {@link feign.ReflectiveFeign.BuildTemplateByResolvingArgs#resolve(Object[], RequestTemplate, Map)}
     *  put it into the {@link PathVarInterceptor#URL_CONTEXT}
     *
     * @param method
     * @param result change this result, if you want to truncate the method.
     * @throws Throwable
     */
    @Override public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments,
        Class<?>[] argumentsTypes, MethodInterceptResult result) throws Throwable {
        RequestTemplate template = (RequestTemplate)allArguments[1];
        URL_CONTEXT.set(template.url());
    }

    /**
     *  do nothing
     * @param method
     * @param ret the method's original return value.
     * @return
     */
    @Override public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments,
        Class<?>[] argumentsTypes, Object ret) {
        return ret;
    }

    @Override public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
        Class<?>[] argumentsTypes, Throwable t) {

    }
}
