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

/**
 * {@link PathVarInterceptor} intercept the Feign RequestTemplate args resolve ;
 */
public class PathVarInterceptor implements InstanceMethodsAroundInterceptor {

    static final ThreadLocal<FeignResolvedURL> URL_CONTEXT = new ThreadLocal<FeignResolvedURL>();

    /**
     * Get the {@link RequestTemplate#url()} before feign.ReflectiveFeign.BuildTemplateByResolvingArgs#resolve(Object[],
     * RequestTemplate, Map) put it into the {@link PathVarInterceptor#URL_CONTEXT}
     *
     * @param method intercept method
     * @param result change this result, if you want to truncate the method.
     */
    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
        MethodInterceptResult result) {
        RequestTemplate template = (RequestTemplate) allArguments[1];
        URL_CONTEXT.set(new FeignResolvedURL(template.url()));
    }

    /**
     * Get the resolved {@link RequestTemplate#url()} after feign.ReflectiveFeign.BuildTemplateByResolvingArgs#resolve(Object[],
     * RequestTemplate, Map) put it into the {@link PathVarInterceptor#URL_CONTEXT}
     *
     * @param method intercept method
     * @param ret    the method's original return value.
     * @return result without change
     */
    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
        Object ret) {
        RequestTemplate resolvedTemplate = (RequestTemplate) ret;
        URL_CONTEXT.get().setUrl(resolvedTemplate.url());
        return ret;
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
        Class<?>[] argumentsTypes, Throwable t) {
        if (URL_CONTEXT.get() != null) {
            URL_CONTEXT.remove();
        }
    }
}
