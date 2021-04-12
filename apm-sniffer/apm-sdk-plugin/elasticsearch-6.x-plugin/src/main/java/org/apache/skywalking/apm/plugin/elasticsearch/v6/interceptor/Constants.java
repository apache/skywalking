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

import org.apache.skywalking.apm.agent.core.context.tag.AbstractTag;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;

public class Constants {
    //interceptor class
    public static final String REST_HIGH_LEVEL_CLIENT_CON_INTERCEPTOR = "org.apache.skywalking.apm.plugin.elasticsearch.v6.interceptor.RestHighLevelClientConInterceptor";
    public static final String INDICES_CLIENT_CREATE_METHODS_INTERCEPTOR = "org.apache.skywalking.apm.plugin.elasticsearch.v6.interceptor.IndicesClientCreateMethodsInterceptor";
    public static final String INDICES_CLIENT_DELETE_METHODS_INTERCEPTOR = "org.apache.skywalking.apm.plugin.elasticsearch.v6.interceptor.IndicesClientDeleteMethodsInterceptor";
    public static final String INDICES_CLIENT_ANALYZE_METHODS_INTERCEPTOR = "org.apache.skywalking.apm.plugin.elasticsearch.v6.interceptor.IndicesClientAnalyzeMethodsInterceptor";
    public static final String REST_HIGH_LEVEL_CLIENT_SEARCH_SCROLL_METHODS_INTERCEPTOR = "org.apache.skywalking.apm.plugin.elasticsearch.v6.interceptor.RestHighLevelClientSearchScrollMethodsInterceptor";
    public static final String REST_HIGH_LEVEL_CLIENT_SEARCH_TEMPLATE_METHODS_INTERCEPTOR = "org.apache.skywalking.apm.plugin.elasticsearch.v6.interceptor.RestHighLevelClientSearchTemplateMethodsInterceptor";
    public static final String REST_HIGH_LEVEL_CLIENT_CLEAR_SCROLL_METHODS_INTERCEPTOR = "org.apache.skywalking.apm.plugin.elasticsearch.v6.interceptor.RestHighLevelClientClearScrollMethodsInterceptor";
    public static final String REST_HIGH_LEVEL_CLIENT_DELETE_BY_QUERY_METHODS_INTERCEPTOR = "org.apache.skywalking.apm.plugin.elasticsearch.v6.interceptor.RestHighLevelClientDeleteByQueryMethodsInterceptor";
    public static final String REST_HIGH_LEVEL_CLIENT_GET_METHODS_INTERCEPTOR = "org.apache.skywalking.apm.plugin.elasticsearch.v6.interceptor.RestHighLevelClientGetMethodsInterceptor";
    public static final String REST_HIGH_LEVEL_CLIENT_SEARCH_METHODS_INTERCEPTOR = "org.apache.skywalking.apm.plugin.elasticsearch.v6.interceptor.RestHighLevelClientSearchMethodsInterceptor";
    public static final String REST_HIGH_LEVEL_CLIENT_UPDATE_METHODS_INTERCEPTOR = "org.apache.skywalking.apm.plugin.elasticsearch.v6.interceptor.RestHighLevelClientUpdateMethodsInterceptor";
    public static final String REST_HIGH_LEVEL_CLIENT_INDEX_METHODS_INTERCEPTOR = "org.apache.skywalking.apm.plugin.elasticsearch.v6.interceptor.RestHighLevelClientIndexMethodsInterceptor";
    public static final String REST_HIGH_LEVEL_CLIENT_INDICES_METHODS_INTERCEPTOR = "org.apache.skywalking.apm.plugin.elasticsearch.v6.interceptor.RestHighLevelClientIndicesMethodsInterceptor";
    public static final String REST_HIGH_LEVEL_CLIENT_CLUSTER_METHODS_INTERCEPTOR = "org.apache.skywalking.apm.plugin.elasticsearch.v6.interceptor.RestHighLevelClientClusterMethodsInterceptor";
    public static final String CLUSTER_CLIENT_HEALTH_METHODS_INTERCEPTOR = "org.apache.skywalking.apm.plugin.elasticsearch.v6.interceptor.ClusterClientHealthMethodsInterceptor";
    public static final String CLUSTER_CLIENT_GET_SETTINGS_METHODS_INTERCEPTOR = "org.apache.skywalking.apm.plugin.elasticsearch.v6.interceptor.ClusterClientGetSettingsMethodsInterceptor";
    public static final String CLUSTER_CLIENT_PUT_SETTINGS_METHODS_INTERCEPTOR = "org.apache.skywalking.apm.plugin.elasticsearch.v6.interceptor.ClusterClientPutSettingsMethodsInterceptor";

    //witnessClasses
    public static final String TASK_TRANSPORT_CHANNEL_WITNESS_CLASSES = "org.elasticsearch.transport.TaskTransportChannel";
    public static final String SEARCH_HITS_WITNESS_CLASSES = "org.elasticsearch.search.SearchHits";

    //es operator name
    public static final String CREATE_OPERATOR_NAME = "Elasticsearch/CreateRequest";
    public static final String DELETE_OPERATOR_NAME = "Elasticsearch/DeleteRequest";
    public static final String ANALYZE_OPERATOR_NAME = "Elasticsearch/AnalyzeRequest";
    public static final String GET_OPERATOR_NAME = "Elasticsearch/GetRequest";
    public static final String INDEX_OPERATOR_NAME = "Elasticsearch/IndexRequest";
    public static final String SEARCH_OPERATOR_NAME = "Elasticsearch/SearchRequest";
    public static final String UPDATE_OPERATOR_NAME = "Elasticsearch/UpdateRequest";
    public static final String SEARCH_SCROLL_OPERATOR_NAME = "Elasticsearch/SearchScrollRequest";
    public static final String SEARCH_TEMPLATE_OPERATOR_NAME = "Elasticsearch/SearchTemplateRequest";
    public static final String CLEAR_SCROLL_OPERATOR_NAME = "Elasticsearch/ClearScrollRequest";
    public static final String DELETE_BY_QUERY_OPERATOR_NAME = "Elasticsearch/DeleteByQueryRequest";
    public static final String CLUSTER_HEALTH_NAME = "Elasticsearch/Health";
    public static final String CLUSTER_GET_SETTINGS_NAME = "Elasticsearch/GetSettings";
    public static final String CLUSTER_PUT_SETTINGS_NAME = "Elasticsearch/PutSettings";

    public static final String DB_TYPE = "Elasticsearch";

    public static final String BASE_FUTURE_METHOD = "actionGet";

    //tags
    public static final AbstractTag<String> ES_NODE = Tags.ofKey("node.address");
    public static final AbstractTag<String> ES_INDEX = Tags.ofKey("es.indices");
    public static final AbstractTag<String> ES_TYPE = Tags.ofKey("es.types");
    public static final AbstractTag<String> ES_TOOK_MILLIS = Tags.ofKey("es.took_millis");
    public static final AbstractTag<String> ES_TOTAL_HITS = Tags.ofKey("es.total_hits");
    public static final AbstractTag<String> ES_INGEST_TOOK_MILLIS = Tags.ofKey("es.ingest_took_millis");
}
