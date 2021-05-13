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
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceConstructorInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;
import org.apache.skywalking.apm.plugin.elasticsearch.v6.TransportClientEnhanceInfo;
import org.apache.skywalking.apm.util.StringUtil;
import org.elasticsearch.action.ActionRequest;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.cluster.node.DiscoveryNode;

import static org.apache.skywalking.apm.plugin.elasticsearch.v6.ElasticsearchPluginConfig.Plugin.Elasticsearch.TRACE_DSL;

public class TransportActionNodeProxyExecuteMethodsInterceptor implements InstanceConstructorInterceptor, InstanceMethodsAroundInterceptor {

    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments,
                             Class<?>[] argumentsTypes, MethodInterceptResult result) throws Throwable {

        TransportClientEnhanceInfo enhanceInfo = (TransportClientEnhanceInfo) objInst.getSkyWalkingDynamicField();
        ActionRequest request = (ActionRequest) allArguments[1];
        String opType = request.getClass().getSimpleName();
        String operationName = Constants.DB_TYPE + "/" + opType;
        AbstractSpan span = ContextManager.createExitSpan(operationName, enhanceInfo.transportAddresses());
        span.setComponent(ComponentsDefine.TRANSPORT_CLIENT);
        Tags.DB_TYPE.set(span, Constants.DB_TYPE);
        Tags.DB_INSTANCE.set(span, enhanceInfo.getClusterName());
        span.tag(Constants.ES_NODE, ((DiscoveryNode) allArguments[0]).getAddress().toString());
        parseRequestInfo(request, span);

        SpanLayer.asDB(span);
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
        ContextManager.activeSpan().log(t);
    }

    @Override
    public void onConstruct(EnhancedInstance objInst, Object[] allArguments) {
        EnhancedInstance actions = (EnhancedInstance) allArguments[2];
        objInst.setSkyWalkingDynamicField(actions.getSkyWalkingDynamicField());
    }

    private void parseRequestInfo(ActionRequest request, AbstractSpan span) {
        // search request
        if (request instanceof SearchRequest) {
            parseSearchRequest((SearchRequest) request, span);
            return;
        }
        // get request
        if (request instanceof GetRequest) {
            parseGetRequest((GetRequest) request, span);
            return;
        }
        // index request
        if (request instanceof IndexRequest) {
            parseIndexRequest((IndexRequest) request, span);
            return;
        }
        // update request
        if (request instanceof UpdateRequest) {
            parseUpdateRequest((UpdateRequest) request, span);
            return;
        }
        // delete request
        if (request instanceof DeleteRequest) {
            parseDeleteRequest((DeleteRequest) request, span);
            return;
        }
        // delete index request
        if (request instanceof DeleteIndexRequest) {
            parseDeleteIndexRequest((DeleteIndexRequest) request, span);
            return;
        }
    }

    private void parseSearchRequest(SearchRequest searchRequest, AbstractSpan span) {
        span.tag(Constants.ES_INDEX, StringUtil.join(',', searchRequest.indices()));
        span.tag(Constants.ES_TYPE, StringUtil.join(',', searchRequest.types()));
        if (TRACE_DSL) {
            Tags.DB_STATEMENT.set(span, searchRequest.toString());
        }
    }

    private void parseGetRequest(GetRequest getRequest, AbstractSpan span) {
        span.tag(Constants.ES_INDEX, getRequest.index());
        span.tag(Constants.ES_TYPE, getRequest.type());
        if (TRACE_DSL) {
            Tags.DB_STATEMENT.set(span, getRequest.toString());
        }
    }

    private void parseIndexRequest(IndexRequest indexRequest, AbstractSpan span) {
        span.tag(Constants.ES_INDEX, indexRequest.index());
        span.tag(Constants.ES_TYPE, indexRequest.type());
        if (TRACE_DSL) {
            Tags.DB_STATEMENT.set(span, indexRequest.toString());
        }
    }

    private void parseUpdateRequest(UpdateRequest updateRequest, AbstractSpan span) {
        span.tag(Constants.ES_INDEX, updateRequest.index());
        span.tag(Constants.ES_TYPE, updateRequest.type());
        if (TRACE_DSL) {
            Tags.DB_STATEMENT.set(span, updateRequest.toString());
        }
    }

    private void parseDeleteRequest(DeleteRequest deleteRequest, AbstractSpan span) {
        span.tag(Constants.ES_INDEX, deleteRequest.index());
        span.tag(Constants.ES_TYPE, deleteRequest.type());
        if (TRACE_DSL) {
            Tags.DB_STATEMENT.set(span, deleteRequest.toString());
        }
    }

    private void parseDeleteIndexRequest(DeleteIndexRequest deleteIndexRequest, AbstractSpan span) {
        span.tag(Constants.ES_INDEX, String.join(",", deleteIndexRequest.indices()));
    }
}
