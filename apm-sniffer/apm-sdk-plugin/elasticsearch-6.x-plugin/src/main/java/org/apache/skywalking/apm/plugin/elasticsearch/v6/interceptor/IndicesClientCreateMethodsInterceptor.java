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
package org.apache.skywalking.apm.plugin.elasticsearch.v6.interceptor;

import static org.apache.skywalking.apm.agent.core.conf.Config.Plugin.Elasticsearch.TRACE_DSL;
import static org.apache.skywalking.apm.plugin.elasticsearch.v6.interceptor.Constants.DB_TYPE;

import java.lang.reflect.Method;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;
import org.apache.skywalking.apm.plugin.elasticsearch.v6.RestClientEnhanceInfo;
import org.elasticsearch.client.indices.CreateIndexRequest;

/**
 * @author aderm
 */
public class IndicesClientCreateMethodsInterceptor implements InstanceMethodsAroundInterceptor {

    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments,
        Class<?>[] argumentsTypes, MethodInterceptResult result) throws Throwable {
        CreateIndexRequest createIndexRequest = (CreateIndexRequest)(allArguments[0]);

        RestClientEnhanceInfo restClientEnhanceInfo = (RestClientEnhanceInfo) (objInst
            .getSkyWalkingDynamicField());
        if (restClientEnhanceInfo != null) {
            AbstractSpan span = ContextManager
                .createExitSpan(Constants.CREATE_OPERATOR_NAME,
                    restClientEnhanceInfo.getPeers());
            span.setComponent(ComponentsDefine.REST_HIGH_LEVEL_CLIENT);

            Tags.DB_TYPE.set(span, DB_TYPE);
            Tags.DB_INSTANCE.set(span, createIndexRequest.index());
            if (TRACE_DSL) {
                //Store es mapping parameters
                Tags.DB_STATEMENT
                    .set(span, createIndexRequest.mappings().utf8ToString());
            }
            SpanLayer.asDB(span);
        }
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments,
        Class<?>[] argumentsTypes, Object ret) throws Throwable {
        RestClientEnhanceInfo restClientEnhanceInfo = (RestClientEnhanceInfo) (objInst
            .getSkyWalkingDynamicField());
        if (restClientEnhanceInfo != null) {
            ContextManager.stopSpan();
        }
        return ret;
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method,
        Object[] allArguments, Class<?>[] argumentsTypes, Throwable t) {
        RestClientEnhanceInfo restClientEnhanceInfo = (RestClientEnhanceInfo) (objInst
            .getSkyWalkingDynamicField());
        if (restClientEnhanceInfo != null) {
            ContextManager.activeSpan().errorOccurred().log(t);
        }
    }
}
