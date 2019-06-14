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

package org.apache.skywalking.apm.plugin.jdbc.mysql;

import org.apache.skywalking.apm.agent.core.conf.Config;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.plugin.jdbc.define.StatementEnhanceInfos;
import org.apache.skywalking.apm.plugin.jdbc.trace.ConnectionInfo;

import java.lang.reflect.Method;

import static org.apache.skywalking.apm.plugin.jdbc.define.Constants.PARAMETER_PLACEHOLDER;
import static org.apache.skywalking.apm.plugin.jdbc.mysql.Constants.DISPLAYABLE_TYPES;
import static org.apache.skywalking.apm.plugin.jdbc.mysql.Constants.PS_IGNORED_SETTERS;
import static org.apache.skywalking.apm.plugin.jdbc.mysql.Constants.PS_SETTERS;
import static org.apache.skywalking.apm.plugin.jdbc.mysql.Constants.SQL_PARAMETERS;
import static org.apache.skywalking.apm.plugin.jdbc.mysql.Constants.SQL_PARAMETER_PLACEHOLDER;

public class PreparedStatementExecuteMethodsInterceptor implements InstanceMethodsAroundInterceptor {
    @Override
    public final void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments,
        Class<?>[] argumentsTypes,
        MethodInterceptResult result) throws Throwable {
        StatementEnhanceInfos cacheObject = (StatementEnhanceInfos)objInst.getSkyWalkingDynamicField();
        ConnectionInfo connectInfo = cacheObject.getConnectionInfo();

        final String methodName = method.getName();

        if (PS_SETTERS.contains(methodName) || PS_IGNORED_SETTERS.contains(methodName)) {
            final int index = (Integer) allArguments[0];
            final Object parameter = getDisplayedParameter(method, argumentsTypes, allArguments);
            cacheObject.setParameter(index, parameter);
        }

        /**
         * For avoid NPE. In this particular case, Execute sql inside the {@link com.mysql.jdbc.ConnectionImpl} constructor,
         * before the interceptor sets the connectionInfo.
         *
         * @see JDBCDriverInterceptor#afterMethod(EnhancedInstance, Method, Object[], Class[], Object)
         */
        if (connectInfo != null && !PS_SETTERS.contains(methodName) && !PS_IGNORED_SETTERS.contains(methodName)) {

            AbstractSpan span = ContextManager.createExitSpan(buildOperationName(connectInfo, methodName, cacheObject.getStatementName()), connectInfo.getDatabasePeer());
            Tags.DB_TYPE.set(span, "sql");
            Tags.DB_INSTANCE.set(span, connectInfo.getDatabaseName());
            Tags.DB_STATEMENT.set(span, cacheObject.getSql());
            span.setComponent(connectInfo.getComponent());

            if (Config.Plugin.MySQL.TRACE_SQL_PARAMETERS) {
                final Object[] parameters = cacheObject.getParameters();
                if (parameters != null && parameters.length > 0) {
                    String parameterString = buildParameterString(parameters);
                    int sqlParametersMaxLength = Config.Plugin.MySQL.SQL_PARAMETERS_MAX_LENGTH;
                    if (sqlParametersMaxLength > 0 && parameterString.length() > sqlParametersMaxLength) {
                        parameterString = parameterString.substring(0, sqlParametersMaxLength) + "...";
                    }
                    SQL_PARAMETERS.set(span, parameterString);
                }
            }

            SpanLayer.asDB(span);
        }
    }

    @Override
    public final Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments,
        Class<?>[] argumentsTypes,
        Object ret) throws Throwable {
        StatementEnhanceInfos cacheObject = (StatementEnhanceInfos)objInst.getSkyWalkingDynamicField();
        ConnectionInfo connectionInfo = cacheObject.getConnectionInfo();
        String methodName = method.getName();
        if (connectionInfo != null && !PS_SETTERS.contains(methodName) && !PS_IGNORED_SETTERS.contains(methodName)) {
            ContextManager.stopSpan();
        }
        return ret;
    }

    @Override public final void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
        Class<?>[] argumentsTypes, Throwable t) {
        StatementEnhanceInfos cacheObject = (StatementEnhanceInfos)objInst.getSkyWalkingDynamicField();
        if (cacheObject.getConnectionInfo() != null) {
            ContextManager.activeSpan().errorOccurred().log(t);
        }
    }

    private String buildOperationName(ConnectionInfo connectionInfo, String methodName, String statementName) {
        return connectionInfo.getDBType() + "/JDBI/" + statementName + "/" + methodName;
    }

    private Object getDisplayedParameter(final Method method, final Class<?>[] argumentTypes, final Object[] allArguments) {
        final String methodName = method.getName();
        if ("setNull".equals(methodName)) {
            return "null";
        }
        if ("setObject".equals(methodName)) {
            final Object parameter = allArguments[0];
            final Class<?> parameterType = argumentTypes[1];

            if (DISPLAYABLE_TYPES.contains(parameterType)) {
                return parameter;
            }
        } else if (PS_SETTERS.contains(methodName)) {
            return allArguments[1];
        }
        return SQL_PARAMETER_PLACEHOLDER;
    }

    private String buildParameterString(Object[] parameters) {
        String parameterString = "[";
        boolean first = true;
        for (Object parameter : parameters) {
            if (parameter == PARAMETER_PLACEHOLDER) {
                break;
            }
            if (!first) {
                parameterString += ",";
            }
            parameterString += parameter;
            first = false;
        }
        parameterString += "]";
        return parameterString;
    }
}
