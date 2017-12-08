/*
 * Copyright 2017, OpenSkywalking Organization All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Project repository: https://github.com/OpenSkywalking/skywalking
 */

package org.skywalking.apm.plugin.mybatis;

import com.alibaba.fastjson.JSON;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.skywalking.apm.agent.core.context.ContextManager;
import org.skywalking.apm.agent.core.context.tag.Tags;
import org.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.skywalking.apm.network.trace.component.ComponentsDefine;

import java.lang.reflect.Method;


public class SimpleExecutorInteceptor implements InstanceMethodsAroundInterceptor {

    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments,
                             Class<?>[] argumentsTypes, MethodInterceptResult result) throws Throwable {
        MappedStatement statement = (MappedStatement) allArguments[0];
        String stateName = statement.getId();
        String methodName = method.getName();
        String sql = "";
        if (allArguments.length == 5) {
            sql = ((BoundSql) allArguments[4]).getSql();
        }

        String sqlParamter = JSON.toJSONString(allArguments[1]);

        try {
            AbstractSpan span = ContextManager.createExitSpan(stateName, "db");
            Tags.DB_TYPE.set(span, "sql");
            Tags.DB_INSTANCE.set(span, methodName);
            Tags.DB_STATEMENT.set(span, sql);
            Tags.DB_BIND_VARIABLES.set(span, sqlParamter);
            span.setComponent(ComponentsDefine.MYBATIS);
            SpanLayer.asDB(span);
        } catch (Exception e) {
            AbstractSpan span = ContextManager.activeSpan();
            span.errorOccurred();
            span.log(e);
            throw e;
        }
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments,
                              Class<?>[] argumentsTypes, Object ret) throws Throwable {
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
        span.errorOccurred();
        span.log(throwable);
    }

//    /**
//     * Format operation name. e.g. org.skywalking.apm.plugin.test.Test.test(String)
//     *
//     * @return operation name.
//     */
//    private String generateOperationName(URL requestURL, Invocation invocation) {
//        StringBuilder operationName = new StringBuilder();
//        operationName.append(requestURL.getPath());
//        operationName.append("." + invocation.getMethodName() + "(");
//        for (Class<?> classes : invocation.getParameterTypes()) {
//            operationName.append(classes.getSimpleName() + ",");
//        }
//
//        if (invocation.getParameterTypes().length > 0) {
//            operationName.delete(operationName.length() - 1, operationName.length());
//        }
//
//        operationName.append(")");
//
//        return operationName.toString();
//    }
//
//    /**
//     * Format request url.
//     * e.g. dubbo://127.0.0.1:20880/org.skywalking.apm.plugin.test.Test.test(String).
//     *
//     * @return request url.
//     */
//    private String generateRequestURL(URL url, Invocation invocation) {
//        StringBuilder requestURL = new StringBuilder();
//        requestURL.append(url.getProtocol() + "://");
//        requestURL.append(url.getHost());
//        requestURL.append(":" + url.getPort() + "/");
//        requestURL.append(generateOperationName(url, invocation));
//        return requestURL.toString();
//    }
}
