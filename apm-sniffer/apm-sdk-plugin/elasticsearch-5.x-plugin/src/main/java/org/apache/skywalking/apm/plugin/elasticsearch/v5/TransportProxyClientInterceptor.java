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

import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceConstructorInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.common.settings.Settings;

import java.lang.reflect.Method;

import static org.apache.skywalking.apm.agent.core.conf.Config.Plugin.Elasticsearch.TRACE_DSL;
import static org.apache.skywalking.apm.plugin.elasticsearch.v5.Constants.ES_ENHANCE_INFO;
import static org.apache.skywalking.apm.plugin.elasticsearch.v5.Util.toArrayString;
import static org.apache.skywalking.apm.plugin.elasticsearch.v5.Util.wrapperNullStringValue;

/**
 * @author oatiz.
 */
public class TransportProxyClientInterceptor implements InstanceConstructorInterceptor, InstanceMethodsAroundInterceptor {

    @Override
    public void onConstruct(EnhancedInstance objInst, Object[] allArguments) {
        Settings settings = (Settings) allArguments[0];
        String clusterName = settings.get("cluster.name");
        objInst.setSkyWalkingDynamicField(wrapperNullStringValue(clusterName));
    }

    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments,
                             Class<?>[] argumentsTypes, MethodInterceptResult result) throws Throwable {
        ElasticSearchEnhanceInfo enhanceInfo = (ElasticSearchEnhanceInfo) ContextManager.getRuntimeContext().get(ES_ENHANCE_INFO);
        enhanceInfo.setClusterName((String) objInst.getSkyWalkingDynamicField());
        parseRequestInfo(allArguments[1], enhanceInfo);
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments,
                              Class<?>[] argumentsTypes, Object ret) throws Throwable {
        return ret;
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
                                      Class<?>[] argumentsTypes, Throwable t) {
        ContextManager.activeSpan().errorOccurred().log(t);
    }

    private void parseRequestInfo(Object request, ElasticSearchEnhanceInfo enhanceInfo) {
        // search request
        if (request instanceof SearchRequest) {
            SearchRequest searchRequest = (SearchRequest) request;
            enhanceInfo.setIndices(toArrayString(searchRequest.indices()));
            enhanceInfo.setTypes(toArrayString(searchRequest.types()));
            enhanceInfo.setOpType(searchRequest.searchType().name());
            if (TRACE_DSL) {
                enhanceInfo.setSource(null == searchRequest.source() ? "" : searchRequest.source().toString());
            }
        }
        // get request
        else if (request instanceof GetRequest) {
            GetRequest getRequest = (GetRequest) request;
            enhanceInfo.setIndices(toArrayString(getRequest.indices()));
            enhanceInfo.setTypes(getRequest.type());
        }
        // index request
        else if (request instanceof IndexRequest) {
            IndexRequest indexRequest = (IndexRequest) request;
            enhanceInfo.setIndices(toArrayString(indexRequest.indices()));
            enhanceInfo.setOpType(indexRequest.opType().name());
            enhanceInfo.setTypes(indexRequest.type());
            if (TRACE_DSL) {
                enhanceInfo.setSource(indexRequest.toString());
            }
        }
        // update request
        else if (request instanceof UpdateRequest) {
            UpdateRequest updateRequest = (UpdateRequest) request;
            enhanceInfo.setIndices(toArrayString(updateRequest.indices()));
            enhanceInfo.setOpType(updateRequest.opType().name());
            enhanceInfo.setTypes(updateRequest.type());
            if (TRACE_DSL) {
                enhanceInfo.setSource(updateRequest.toString());
            }
        }
        // delete request
        else if (request instanceof DeleteRequest) {
            DeleteRequest deleteRequest = (DeleteRequest) request;
            enhanceInfo.setIndices(toArrayString(deleteRequest.indices()));
            enhanceInfo.setOpType(deleteRequest.opType().name());
            enhanceInfo.setTypes(deleteRequest.type());
            if (TRACE_DSL) {
                enhanceInfo.setSource(deleteRequest.toString());
            }
        }
        // bulk request
        else if (request instanceof BulkRequest) {
            BulkRequest bulkRequest = (BulkRequest) request;
            enhanceInfo.setOpType("BULK-" + bulkRequest.numberOfActions());
        }
    }

}
