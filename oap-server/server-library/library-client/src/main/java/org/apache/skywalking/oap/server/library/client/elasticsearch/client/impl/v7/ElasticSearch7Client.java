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

package org.apache.skywalking.oap.server.library.client.elasticsearch.client.impl.v7;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.ContentType;
import org.apache.http.nio.entity.NStringEntity;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchInsertRequest;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchUpdateRequest;
import org.apache.skywalking.oap.server.library.client.elasticsearch.client.impl.v6.ElasticSearch6Client;
import org.elasticsearch.action.admin.indices.create.CreateIndexRequest;
import org.elasticsearch.action.admin.indices.create.CreateIndexResponse;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Response;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.builder.SearchSourceBuilder;

/**
 * An ElasticSearch 7 client that uses ElasticSearch 6 client to adapt the requests.
 *
 * @author kezhenxu94
 */
@Slf4j
public class ElasticSearch7Client extends ElasticSearch6Client {
    private static final String DOC = "_doc";
    private static final String UPDATE = "_update";
    public static final String IF_SEQ_NO = "if_seq_no";
    public static final String SEQ_NO = "_seq_no";
    public static final String IF_PRIMARY_TERM = "if_primary_term";
    public static final String PRIMARY_TERM = "_primary_term";

    public ElasticSearch7Client(String clusterNodes, String protocol,
                                String trustStorePath, String trustStorePass,
                                String namespace, String user, String password) {
        super(clusterNodes, protocol, trustStorePath, trustStorePass, namespace, user, password);
    }

    @Override
    public boolean createIndex(String indexName, JsonObject settings, JsonObject mapping) throws IOException {
        indexName = formatIndexName(indexName);
        CreateIndexRequest request = new CreateIndexRequest(indexName);
        request.settings(settings.toString(), XContentType.JSON);
        request.mapping("properties", mapping.getAsJsonObject("properties").toString(), XContentType.JSON);
        CreateIndexResponse response = client.indices().create(request);
        log.debug("create {} index finished, isAcknowledged: {}", indexName, response.isAcknowledged());
        return response.isAcknowledged();
    }

    @Override
    public SearchResponse search(String indexName, SearchSourceBuilder searchSourceBuilder) throws IOException {
        indexName = formatIndexName(indexName);
        SearchRequest searchRequest = new SearchRequest(indexName);
        searchRequest.source(searchSourceBuilder);
        return client.search(searchRequest);
    }

    @Override
    public GetResponse get(String indexName, String id) throws IOException {
        indexName = formatIndexName(indexName);
        GetRequest request = new GetRequest(indexName, DOC, id);
        return client.get(request);
    }

    @Override
    public SearchResponse ids(String indexName, String[] ids) throws IOException {
        indexName = formatIndexName(indexName);

        SearchRequest searchRequest = new SearchRequest(indexName);
        searchRequest.source().query(QueryBuilders.idsQuery().addIds(ids)).size(ids.length);
        return client.search(searchRequest);
    }

    @Override
    public void forceUpdate(String indexName, String id, JsonObject source, long seqNumber, long primaryTerm) throws IOException {
        Map<String, String> params = new HashMap<>();
        params.put(IF_SEQ_NO, String.valueOf(seqNumber));
        params.put(IF_PRIMARY_TERM, String.valueOf(primaryTerm));
        HttpEntity entity = new NStringEntity(source.toString(), ContentType.APPLICATION_JSON);
        Response response = client.getLowLevelClient().performRequest(HttpPut.METHOD_NAME, "/" + indexName + "/" + DOC + "/" + id, params, entity);
        if (HttpStatus.SC_OK != response.getStatusLine().getStatusCode()) {
            log.error("Failed to update doc in ElasticSearch: {}", response);
            throw new RuntimeException("Failed to update doc in ElasticSearch");
        }
    }

    @Override
    public ElasticSearchInsertRequest prepareInsert(String indexName, String id, XContentBuilder source) {
        indexName = formatIndexName(indexName);
        return new ElasticSearchInsertRequest(indexName, DOC, id).source(source);
    }

    @Override
    public ElasticSearchUpdateRequest prepareUpdate(String indexName, String id, XContentBuilder source) {
        indexName = formatIndexName(indexName);
        return new ElasticSearchUpdateRequest(indexName, DOC, id).doc(source);
    }

}
