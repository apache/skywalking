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

package org.apache.skywalking.apm.plugin.neo4j.v4x;

import static org.apache.skywalking.apm.network.trace.component.ComponentsDefine.NEO4J;
import static org.apache.skywalking.apm.plugin.neo4j.v4x.Neo4jPluginConstants.DB_TYPE;

import java.lang.reflect.Method;
import java.util.concurrent.CompletionStage;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.neo4j.driver.internal.spi.Connection;

/**
 * This interceptor is used to enhance {@link org.neo4j.driver.internal.async.NetworkSession#acquireConnection} method,
 * which is used to obtain connection information and save it in skywalking dynamic field.
 */
public class SessionAcquireConnectionInterceptor implements InstanceMethodsAroundInterceptor {

    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
            MethodInterceptResult result) throws Throwable {
        SessionRequiredInfo requiredInfo = (SessionRequiredInfo) objInst.getSkyWalkingDynamicField();
        if (requiredInfo == null) {
            return;
        }

        final AbstractSpan span = ContextManager.createExitSpan("Neo4j", "Unset");
        Tags.DB_TYPE.set(span, DB_TYPE);
        span.setComponent(NEO4J);
        SpanLayer.asDB(span);
        ContextManager.continued(requiredInfo.getContextSnapshot());
        span.prepareForAsync();
        ContextManager.stopSpan();
        requiredInfo.setSpan(span);
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
            Object ret) throws Throwable {
        SessionRequiredInfo requiredInfo = (SessionRequiredInfo) objInst.getSkyWalkingDynamicField();
        if (requiredInfo == null) {
            return ret;
        }

        CompletionStage<Connection> connectionStage = (CompletionStage<Connection>) ret;
        return connectionStage.thenApply(connection -> {
            if (connection == null) {
                return null;
            }

            try {
                final AbstractSpan span = requiredInfo.getSpan();
                span.setPeer(connection.serverAddress().toString());
                Tags.DB_INSTANCE
                        .set(span, connection.databaseName().databaseName().orElse(Neo4jPluginConstants.EMPTY_STRING));
            } catch (Exception e) {
                // ignore
            }

            return connection;
        });
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
            Class<?>[] argumentsTypes, Throwable t) {
    }
}
