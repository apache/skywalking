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

import java.lang.reflect.Method;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;
import org.apache.skywalking.apm.util.StringUtil;
import org.elasticsearch.action.ActionResponse;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.update.UpdateResponse;

import static org.apache.skywalking.apm.plugin.elasticsearch.v6.ElasticsearchPluginConfig.Plugin.Elasticsearch.ELASTICSEARCH_DSL_LENGTH_THRESHOLD;
import static org.apache.skywalking.apm.plugin.elasticsearch.v6.ElasticsearchPluginConfig.Plugin.Elasticsearch.TRACE_DSL;

public class AdapterActionFutureActionGetMethodsInterceptor implements InstanceMethodsAroundInterceptor {

    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments,
                             Class<?>[] argumentsTypes, MethodInterceptResult result) throws Throwable {

        if (isTrace(objInst)) {
            AbstractSpan span = ContextManager.createLocalSpan(Constants.DB_TYPE + "/" + Constants.BASE_FUTURE_METHOD);
            span.setComponent(ComponentsDefine.TRANSPORT_CLIENT);
            Tags.DB_TYPE.set(span, Constants.DB_TYPE);
        }
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments,
                              Class<?>[] argumentsTypes, Object ret) throws Throwable {

        if (isTrace(objInst)) {
            AbstractSpan span = ContextManager.activeSpan();
            parseResponseInfo((ActionResponse) ret, span);
            ContextManager.stopSpan();
        }
        return ret;
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
                                      Class<?>[] argumentsTypes, Throwable t) {
        if (isTrace(objInst)) {
            ContextManager.activeSpan().log(t);
        }
    }

    private boolean isTrace(EnhancedInstance objInst) {
        return objInst.getSkyWalkingDynamicField() != null && (boolean) objInst.getSkyWalkingDynamicField();
    }

    private void parseResponseInfo(ActionResponse response, AbstractSpan span) {
        // search response
        if (response instanceof SearchResponse) {
            parseSearchResponse((SearchResponse) response, span);
            return;
        }
        // bulk response
        if (response instanceof BulkResponse) {
            parseBulkResponse((BulkResponse) response, span);
            return;
        }
        // get response
        if (response instanceof GetResponse) {
            parseGetResponse((GetResponse) response, span);
            return;
        }
        // index response
        if (response instanceof IndexResponse) {
            parseIndexResponse((IndexResponse) response, span);
            return;
        }
        // update response
        if (response instanceof UpdateResponse) {
            parseUpdateResponse((UpdateResponse) response, span);
            return;
        }
        // delete response
        if (response instanceof DeleteResponse) {
            parseDeleteResponse((DeleteResponse) response, span);
            return;
        }
    }

    private void parseSearchResponse(SearchResponse searchResponse, AbstractSpan span) {
        span.tag(Constants.ES_TOOK_MILLIS, Long.toString(searchResponse.getTook().getMillis()));
        span.tag(Constants.ES_TOTAL_HITS, Long.toString(searchResponse.getHits().getTotalHits()));
        if (TRACE_DSL) {
            String tagValue = searchResponse.toString();
            tagValue = ELASTICSEARCH_DSL_LENGTH_THRESHOLD > 0 ? StringUtil.cut(tagValue, ELASTICSEARCH_DSL_LENGTH_THRESHOLD) : tagValue;
            Tags.DB_STATEMENT.set(span, tagValue);
        }
    }

    private void parseBulkResponse(BulkResponse bulkResponse, AbstractSpan span) {
        span.tag(Constants.ES_TOOK_MILLIS, Long.toString(bulkResponse.getTook().getMillis()));
        span.tag(Constants.ES_INGEST_TOOK_MILLIS, Long.toString(bulkResponse.getIngestTookInMillis()));
        if (TRACE_DSL) {
            String tagValue = bulkResponse.toString();
            tagValue = ELASTICSEARCH_DSL_LENGTH_THRESHOLD > 0 ? StringUtil.cut(tagValue, ELASTICSEARCH_DSL_LENGTH_THRESHOLD) : tagValue;
            Tags.DB_STATEMENT.set(span, tagValue);
        }
    }

    private void parseGetResponse(GetResponse getResponse, AbstractSpan span) {
        if (TRACE_DSL) {
            String tagValue = getResponse.toString();
            tagValue = ELASTICSEARCH_DSL_LENGTH_THRESHOLD > 0 ? StringUtil.cut(tagValue, ELASTICSEARCH_DSL_LENGTH_THRESHOLD) : tagValue;
            Tags.DB_STATEMENT.set(span, tagValue);
        }
    }

    private void parseIndexResponse(IndexResponse indexResponse, AbstractSpan span) {
        if (TRACE_DSL) {
            String tagValue = indexResponse.toString();
            tagValue = ELASTICSEARCH_DSL_LENGTH_THRESHOLD > 0 ? StringUtil.cut(tagValue, ELASTICSEARCH_DSL_LENGTH_THRESHOLD) : tagValue;
            Tags.DB_STATEMENT.set(span, tagValue);
        }
    }

    private void parseUpdateResponse(UpdateResponse updateResponse, AbstractSpan span) {
        if (TRACE_DSL) {
            String tagValue = updateResponse.toString();
            tagValue = ELASTICSEARCH_DSL_LENGTH_THRESHOLD > 0 ? StringUtil.cut(tagValue, ELASTICSEARCH_DSL_LENGTH_THRESHOLD) : tagValue;
            Tags.DB_STATEMENT.set(span, tagValue);
        }
    }

    private void parseDeleteResponse(DeleteResponse deleteResponse, AbstractSpan span) {
        if (TRACE_DSL) {
            String tagValue = deleteResponse.toString();
            tagValue = ELASTICSEARCH_DSL_LENGTH_THRESHOLD > 0 ? StringUtil.cut(tagValue, ELASTICSEARCH_DSL_LENGTH_THRESHOLD) : tagValue;
            Tags.DB_STATEMENT.set(span, tagValue);
        }
    }
}
