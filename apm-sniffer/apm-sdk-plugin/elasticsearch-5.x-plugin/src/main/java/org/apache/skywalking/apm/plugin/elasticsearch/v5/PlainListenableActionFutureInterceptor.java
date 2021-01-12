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

package org.apache.skywalking.apm.plugin.elasticsearch.v5;

import java.lang.reflect.Method;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.search.SearchResponse;

import static org.apache.skywalking.apm.plugin.elasticsearch.v5.Constants.BASE_FUTURE_METHOD;
import static org.apache.skywalking.apm.plugin.elasticsearch.v5.Constants.DB_TYPE;
import static org.apache.skywalking.apm.plugin.elasticsearch.v5.Constants.ELASTICSEARCH_DB_OP_PREFIX;
import static org.apache.skywalking.apm.plugin.elasticsearch.v5.Constants.ES_INGEST_TOOK_MILLIS;
import static org.apache.skywalking.apm.plugin.elasticsearch.v5.Constants.ES_TOOK_MILLIS;
import static org.apache.skywalking.apm.plugin.elasticsearch.v5.Constants.ES_TOTAL_HITS;

public class PlainListenableActionFutureInterceptor implements InstanceMethodsAroundInterceptor {

    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
        MethodInterceptResult result) throws Throwable {
        AbstractSpan span = ContextManager.createLocalSpan(ELASTICSEARCH_DB_OP_PREFIX + BASE_FUTURE_METHOD);
        span.setComponent(ComponentsDefine.TRANSPORT_CLIENT);
        Tags.DB_TYPE.set(span, DB_TYPE);
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
        Object ret) throws Throwable {
        AbstractSpan span = ContextManager.activeSpan();
        if (ret instanceof SearchResponse) {
            SearchResponse response = (SearchResponse) ret;
            span.tag(ES_TOOK_MILLIS, Long.toString(response.getTookInMillis()));
            span.tag(ES_TOTAL_HITS, Long.toString(response.getHits().getTotalHits()));
        } else if (ret instanceof BulkResponse) {
            BulkResponse response = (BulkResponse) ret;
            span.tag(ES_TOOK_MILLIS, Long.toString(response.getTookInMillis()));
            span.tag(ES_INGEST_TOOK_MILLIS, Long.toString(response.getIngestTookInMillis()));
        }
        ContextManager.stopSpan();
        return ret;
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
        Class<?>[] argumentsTypes, Throwable t) {
        ContextManager.activeSpan().log(t);
    }
}
