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

package org.apache.skywalking.apm.plugin.xxljob;

import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceConstructorInterceptor;

import java.lang.reflect.Method;

/**
 * Intercept method of {@link com.xxl.job.core.handler.impl.MethodJobHandler#MethodJobHandler(java.lang.Object, java.lang.reflect.Method, java.lang.reflect.Method, java.lang.reflect.Method)}.
 * cache the job target method details.
 */
public class MethodJobHandlerConstructorInterceptor implements InstanceConstructorInterceptor {

    @Override
    public void onConstruct(EnhancedInstance objInst, Object[] allArguments) throws Throwable {
        Method method = (Method) allArguments[1];
        String methodName = buildMethodName(method);

        objInst.setSkyWalkingDynamicField(methodName);
    }

    protected String buildMethodName(Method method) {
        String className = method.getDeclaringClass().getName();
        String methodName = method.getName();

        return className + "." + methodName;
    }
}
