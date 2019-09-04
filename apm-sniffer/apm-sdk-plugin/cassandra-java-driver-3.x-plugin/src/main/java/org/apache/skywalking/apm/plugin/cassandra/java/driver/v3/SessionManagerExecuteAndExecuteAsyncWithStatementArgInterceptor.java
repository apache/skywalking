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

package org.apache.skywalking.apm.plugin.cassandra.java.driver.v3;

import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.Statement;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;

import java.lang.reflect.Method;

/**
 * @author stone.wlg
 */
public class SessionManagerExecuteAndExecuteAsyncWithStatementArgInterceptor implements InstanceMethodsAroundInterceptor {

    @Override
    public final void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments,
                                   Class<?>[] argumentsTypes,
                                   MethodInterceptResult result) throws Throwable {
        ConnectionInfo connectionInfo = (ConnectionInfo) objInst.getSkyWalkingDynamicField();
        if (connectionInfo == null) {
            return;
        }

        Statement statement = (Statement) allArguments[0];
        String remotePeer = statement.getHost() == null ? connectionInfo.getContactPoints() : (statement.getHost().getSocketAddress().getHostName() + ":" + statement.getHost().getSocketAddress().getPort());
        String keyspace = statement.getKeyspace() == null ? connectionInfo.getKeyspace() : statement.getKeyspace();
        String query = statement.toString();
        if (statement instanceof BoundStatement) {
            query = ((BoundStatement) statement).preparedStatement().getQueryString();
        }

        AbstractSpan span = ContextManager.createExitSpan(Constants.CASSANDRA_OP_PREFIX + method.getName(), remotePeer);
        span.setComponent(ComponentsDefine.CASSANDRA_JAVA_DRIVER);
        Tags.DB_TYPE.set(span, Constants.CASSANDRA_DB_TYPE);
        Tags.DB_INSTANCE.set(span, keyspace);
        Tags.DB_STATEMENT.set(span, query);
        SpanLayer.asDB(span);
    }

    @Override
    public final Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments,
                                    Class<?>[] argumentsTypes,
                                    Object ret) throws Throwable {
        ConnectionInfo connectionInfo = (ConnectionInfo) objInst.getSkyWalkingDynamicField();
        if (connectionInfo != null && ContextManager.isActive()) {
            ContextManager.stopSpan();
        }
        return ret;
    }

    @Override
    public final void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
                                            Class<?>[] argumentsTypes, Throwable t) {
        if (ContextManager.isActive()) {
            AbstractSpan span = ContextManager.activeSpan();
            span.errorOccurred();
            span.log(t);
        }
    }
}
