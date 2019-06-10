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

import com.google.common.base.Joiner;
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
import java.util.Map;

import static org.apache.skywalking.apm.plugin.jdbc.mysql.Constants.SQL_PARAMETERS;

public class PreparedStatementExecuteMethodsInterceptor implements InstanceMethodsAroundInterceptor {

    @Override
    public final void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments,
        Class<?>[] argumentsTypes,
        MethodInterceptResult result) throws Throwable {
        StatementEnhanceInfos cacheObject = (StatementEnhanceInfos)objInst.getSkyWalkingDynamicField();
        ConnectionInfo connectInfo = cacheObject.getConnectionInfo();

        final String methodName = method.getName();
        final boolean traceSqlParameters = Config.Plugin.MySQL.TRACE_SQL_PARAMETERS;

        if (traceSqlParameters && methodName.startsWith("set")) {
            final int index = (Integer) allArguments[0];
            final Object parameter = allArguments[1];
            cacheObject.setParameter(index, parameter);
        }

        /**
         * For avoid NPE. In this particular case, Execute sql inside the {@link com.mysql.jdbc.ConnectionImpl} constructor,
         * before the interceptor sets the connectionInfo.
         *
         * @see JDBCDriverInterceptor#afterMethod(EnhancedInstance, Method, Object[], Class[], Object)
         */
        if (connectInfo != null) {

            AbstractSpan span = ContextManager.createExitSpan(buildOperationName(connectInfo, methodName, cacheObject.getStatementName()), connectInfo.getDatabasePeer());
            Tags.DB_TYPE.set(span, "sql");
            Tags.DB_INSTANCE.set(span, connectInfo.getDatabaseName());
            Tags.DB_STATEMENT.set(span, cacheObject.getSql());
            span.setComponent(connectInfo.getComponent());

            if (traceSqlParameters) {
                final Map<Integer, Object> parametersKeyedByIndex = cacheObject.getParameters();
                if (!parametersKeyedByIndex.isEmpty()) {
                    final String parameters = Joiner.on(",").join(parametersKeyedByIndex.values());
                    SQL_PARAMETERS.set(span, parameters);
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
        if (cacheObject.getConnectionInfo() != null) {
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
}
