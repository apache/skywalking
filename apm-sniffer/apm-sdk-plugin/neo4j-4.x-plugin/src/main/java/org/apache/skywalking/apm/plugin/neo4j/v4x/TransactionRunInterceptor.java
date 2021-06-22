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

import java.lang.reflect.Method;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.plugin.neo4j.v4x.Neo4jPluginConfig.Plugin.Neo4j;
import org.apache.skywalking.apm.plugin.neo4j.v4x.util.CypherUtils;
import org.neo4j.driver.Query;

/**
 * This interceptor do the following steps:
 * <pre>
 * 1. Create exit span before method, and set related tags.
 * 2. Call {@link AbstractSpan#prepareForAsync()} and {@link ContextManager#stopSpan()} method.
 * 3. Save span into skywalking dynamic field.
 * 4. Return a new CompletionStage after method and async finish the span.
 * </pre>
 */
public class TransactionRunInterceptor implements InstanceMethodsAroundInterceptor {

    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
            MethodInterceptResult result) throws Throwable {
        final Query query = (Query) allArguments[0];
        SessionRequiredInfo requiredInfo = (SessionRequiredInfo) objInst.getSkyWalkingDynamicField();
        if (query == null || requiredInfo == null || requiredInfo.getSpan() == null) {
            return;
        }

        final AbstractSpan span = requiredInfo.getSpan();
        span.setOperationName("Neo4j/Transaction/" + method.getName());
        Tags.DB_STATEMENT.set(span, CypherUtils.limitBodySize(query.text()));
        if (Neo4j.TRACE_CYPHER_PARAMETERS) {
            Neo4jPluginConstants.CYPHER_PARAMETERS_TAG
                    .set(span, CypherUtils.limitParametersSize(query.parameters().toString()));
        }
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
            Object ret) throws Throwable {
        SessionRequiredInfo requiredInfo = (SessionRequiredInfo) objInst.getSkyWalkingDynamicField();
        if (requiredInfo == null || requiredInfo.getSpan() == null) {
            return ret;
        }

        requiredInfo.getSpan().asyncFinish();
        return ret;
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
            Class<?>[] argumentsTypes, Throwable t) {
        AbstractSpan span = ((SessionRequiredInfo) objInst.getSkyWalkingDynamicField()).getSpan();
        if (span != null) {
            span.log(t);
        }
    }
}
