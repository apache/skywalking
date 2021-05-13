/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.apache.skywalking.apm.plugin.elasticsearch.v5;

import java.io.IOException;
import java.util.List;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceConstructorInterceptor;
import org.apache.skywalking.apm.util.StringUtil;
import org.elasticsearch.action.GenericAction;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentFactory;

import static org.apache.skywalking.apm.plugin.elasticsearch.v5.ElasticsearchPluginConfig.Plugin.Elasticsearch.TRACE_DSL;

public class TransportProxyClientInterceptor implements InstanceConstructorInterceptor {

    private static final ILog LOGGER = LogManager.getLogger(TransportProxyClientInterceptor.class);

    @Override
    public void onConstruct(EnhancedInstance objInst, Object[] allArguments) {
        Settings settings = (Settings) allArguments[0];
        String clusterName = settings.get("cluster.name");

        EnhancedInstance nodeService = (EnhancedInstance) allArguments[2];
        List<GenericAction> genericActions = (List<GenericAction>) allArguments[3];

        for (GenericAction action : genericActions) {
            if (action instanceof EnhancedInstance) {
                ElasticSearchEnhanceInfo elasticSearchEnhanceInfo = new ElasticSearchEnhanceInfo();
                elasticSearchEnhanceInfo.setClusterName(clusterName);
                parseRequestInfo(action, elasticSearchEnhanceInfo);
                elasticSearchEnhanceInfo.setTransportAddressHolder(nodeService);
                ((EnhancedInstance) action).setSkyWalkingDynamicField(elasticSearchEnhanceInfo);
            }
        }
    }

    private void parseRequestInfo(Object request, ElasticSearchEnhanceInfo enhanceInfo) {
        // search request
        if (request instanceof SearchRequest) {
            parseSearchRequest(request, enhanceInfo);
            return;
        }
        // get request
        if (request instanceof GetRequest) {
            parseGetRequest(request, enhanceInfo);
            return;
        }
        // index request
        if (request instanceof IndexRequest) {
            parseIndexRequest(request, enhanceInfo);
            return;
        }
        // update request
        if (request instanceof UpdateRequest) {
            parseUpdateRequest(request, enhanceInfo);
            return;
        }
        // delete request
        if (request instanceof DeleteRequest) {
            parseDeleteRequest(request, enhanceInfo);
        }
    }

    private void parseSearchRequest(Object request, ElasticSearchEnhanceInfo enhanceInfo) {
        SearchRequest searchRequest = (SearchRequest) request;
        enhanceInfo.setIndices(StringUtil.join(',', searchRequest.indices()));
        enhanceInfo.setTypes(StringUtil.join(',', searchRequest.types()));
        if (TRACE_DSL) {
            enhanceInfo.setSource(null == searchRequest.source() ? "" : searchRequest.source().toString());
        }
    }

    private void parseGetRequest(Object request, ElasticSearchEnhanceInfo enhanceInfo) {
        GetRequest getRequest = (GetRequest) request;
        enhanceInfo.setIndices(StringUtil.join(',', getRequest.indices()));
        enhanceInfo.setTypes(getRequest.type());
        if (TRACE_DSL) {
            enhanceInfo.setSource(getRequest.toString());
        }
    }

    private void parseIndexRequest(Object request, ElasticSearchEnhanceInfo enhanceInfo) {
        IndexRequest indexRequest = (IndexRequest) request;
        enhanceInfo.setIndices(StringUtil.join(',', indexRequest.indices()));
        enhanceInfo.setTypes(indexRequest.type());
        if (TRACE_DSL) {
            enhanceInfo.setSource(indexRequest.toString());
        }
    }

    private void parseUpdateRequest(Object request, ElasticSearchEnhanceInfo enhanceInfo) {
        UpdateRequest updateRequest = (UpdateRequest) request;
        enhanceInfo.setIndices(StringUtil.join(',', updateRequest.indices()));
        enhanceInfo.setTypes(updateRequest.type());
        if (TRACE_DSL) {
            String updateDsl = "";
            try {
                updateDsl = updateRequest.toXContent(XContentFactory.jsonBuilder(), null).string();
            } catch (IOException e) {
                LOGGER.warn("trace update request dsl error: ", e);
            }
            enhanceInfo.setSource(updateDsl);
        }
    }

    private void parseDeleteRequest(Object request, ElasticSearchEnhanceInfo enhanceInfo) {
        DeleteRequest deleteRequest = (DeleteRequest) request;
        enhanceInfo.setIndices(StringUtil.join(',', deleteRequest.indices()));
        enhanceInfo.setTypes(deleteRequest.type());
        if (TRACE_DSL) {
            enhanceInfo.setSource(deleteRequest.toString());
        }
    }

}
