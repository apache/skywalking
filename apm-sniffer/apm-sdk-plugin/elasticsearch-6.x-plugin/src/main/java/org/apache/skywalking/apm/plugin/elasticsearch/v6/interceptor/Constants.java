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

/**
 * @author aderm
 */
public class Constants {
    //interceptor class
    public static final String REST_HIGH_LEVEL_CLIENT_CON_INTERCEPTOR = "org.apache.skywalking.apm.plugin.elasticsearch.v6.interceptor.RestHighLevelClientConInterceptor";
    public static final String INDICES_CLIENT_CREATE_METHODS_INTERCEPTOR = "org.apache.skywalking.apm.plugin.elasticsearch.v6.interceptor.IndicesClientCreateMethodsInterceptor";
    public static final String INDICES_CLIENT_DELETE_METHODS_INTERCEPTOR = "org.apache.skywalking.apm.plugin.elasticsearch.v6.interceptor.IndicesClientDeleteMethodsInterceptor";
    public static final String REST_HIGH_LEVEL_CLIENT_GET_METHODS_INTERCEPTOR = "org.apache.skywalking.apm.plugin.elasticsearch.v6.interceptor.RestHighLevelClientGetMethodsInterceptor";
    public static final String REST_HIGH_LEVEL_CLIENT_SEARCH_METHODS_INTERCEPTOR = "org.apache.skywalking.apm.plugin.elasticsearch.v6.interceptor.RestHighLevelClientSearchMethodsInterceptor";
    public static final String REST_HIGH_LEVEL_CLIENT_UPDATE_METHODS_INTERCEPTOR = "org.apache.skywalking.apm.plugin.elasticsearch.v6.interceptor.RestHighLevelClientUpdateMethodsInterceptor";
    public static final String REST_HIGH_LEVEL_CLIENT_INDEX_METHODS_INTERCEPTOR = "org.apache.skywalking.apm.plugin.elasticsearch.v6.interceptor.RestHighLevelClientIndexMethodsInterceptor";
    public static final String REST_HIGH_LEVEL_CLIENT_INDICES_METHODS_INTERCEPTOR = "org.apache.skywalking.apm.plugin.elasticsearch.v6.interceptor.RestHighLevelClientIndicesMethodsInterceptor";

    //es operator name
    public static final String CREATE_OPERATOR_NAME = "Elasticsearch/CreateRequest";
    public static final String DELETE_OPERATOR_NAME = "Elasticsearch/DeleteRequest";
    public static final String GET_OPERATOR_NAME = "Elasticsearch/GetRequest";
    public static final String INDEX_OPERATOR_NAME = "Elasticsearch/IndexRequest";
    public static final String SEARCH_OPERATOR_NAME = "Elasticsearch/SearchRequest";
    public static final String UPDATE_OPERATOR_NAME = "Elasticsearch/UpdateRequest";

    public static final String DB_TYPE = "Elasticsearch";
}
