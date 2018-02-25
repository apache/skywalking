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


package org.apache.skywalking.apm.collector.client.elasticsearch.http;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.skywalking.apm.collector.client.Client;
import org.apache.skywalking.apm.collector.client.ClientException;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonArray;

import io.searchbox.action.Action;
import io.searchbox.client.JestClient;
import io.searchbox.client.JestClientFactory;
import io.searchbox.client.JestResult;
import io.searchbox.client.config.HttpClientConfig;
import io.searchbox.core.Delete;
import io.searchbox.core.DeleteByQuery;
import io.searchbox.core.DocumentResult;
import io.searchbox.core.Get;
import io.searchbox.core.Index;
import io.searchbox.core.MultiGet;
import io.searchbox.core.Search;
import io.searchbox.core.Update;
import io.searchbox.indices.CreateIndex;
import io.searchbox.indices.IndicesExists;
import io.searchbox.indices.mapping.PutMapping;
import io.searchbox.params.Parameters;

/**
 * @author cyberdak
 */
public class ElasticSearchHttpClient implements Client {

    private final Logger logger = LoggerFactory.getLogger(ElasticSearchHttpClient.class);

    private JestClient client;

    private final String clusterName;

    private final String clusterNodes;

    private final Boolean ssl;

    private final String userName;

    private final String password;

    public ElasticSearchHttpClient(String clusterName, String clusterNodes, Boolean ssl,String userName,String password) {
        this.clusterName = clusterName;
        this.clusterNodes = clusterNodes;
        this.ssl = ssl;
        this.userName = userName;
        this.password = password;
    }

    private String makeServers() {
        String schema = ssl ? "https" : "http";
        return schema + "://" + clusterNodes;
    }

    @Override public void initialize() throws ClientException {
        JestClientFactory factory = new JestClientFactory();
        factory.setHttpClientConfig(new HttpClientConfig
                .Builder(makeServers())
                .multiThreaded(true)
                .discoveryFrequency(1, TimeUnit.MINUTES)
                .discoveryEnabled(true)
                .defaultCredentials(userName, password)
                //Per default this implementation will create no more than 2 concurrent connections per given route
                .defaultMaxTotalConnectionPerRoute(1)
                // and no more 20 connections in total
                .maxTotalConnection(10)
                .build());

        client = factory.getObject();

    }

    @Override public void shutdown() {

    }

    public <T extends JestResult> T execute(Action<T> search) {
        try {
            return client.execute(search);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
    
    public JestResult execute(MultiGet search) {
        try {
            return client.execute(search);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
    
    public JsonArray executeForJsonArray(Search search) {
        try {
            return client.execute(search).getJsonObject().getAsJsonObject("hits").getAsJsonArray("hits");
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public boolean createIndex(String index,String type, Settings settings,String mapping) {
        try {
            client.execute(new CreateIndex.Builder(index).settings(settings.getAsMap()).build());
            PutMapping putMapping = new PutMapping.Builder(
                    index,
                    type,
                    mapping
                    ).build();
            client.execute(putMapping);
            return true;
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            return false;
        }
    }

    public boolean createIndex(String index,String type, Settings settings,XContentBuilder mapping) {
        try {
           return  createIndex(index,type,settings,mapping.string());
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }
    
    public boolean deleteIndex(String indexName) {
        try {
            JestResult result =  client.execute(new Delete.Builder("1")
                    .index("twitter")
                    .type("tweet")
                    .build());
            return result.isSucceeded();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return false;
    }
    //
    //    public boolean createIndex(String indexName, String indexType, Settings settings, XContentBuilder mappingBuilder) {
    //        IndicesAdminClient adminClient = client.admin().indices();
    //        CreateIndexResponse response = adminClient.prepareCreate(indexName).setSettings(settings).addMapping(indexType, mappingBuilder).get();
    //        logger.info("create {} index with type of {} finished, isAcknowledged: {}", indexName, indexType, response.isAcknowledged());
    //        return response.isShardsAcked();
    //    }
    //
    //    public boolean deleteIndex(String indexName) {
    //        IndicesAdminClient adminClient = client.admin().indices();
    //        DeleteIndexResponse response = adminClient.prepareDelete(indexName).get();
    //        logger.info("delete {} index finished, isAcknowledged: {}", indexName, response.isAcknowledged());
    //        return response.isAcknowledged();
    //    }
    //
    //    public boolean isExistsIndex(String indexName) {
    //        IndicesAdminClient adminClient = client.admin().indices();
    //        IndicesExistsResponse response = adminClient.prepareExists(indexName).get();
    //        return response.isExists();
    //    }
    //
    //    public SearchRequestBuilder prepareSearch(String indexName) {
    //        return client.prepareSearch(indexName);
    //    }
    //
    //    public IndexRequestBuilder prepareIndex(String indexName, String id) {
    //        return client.prepareIndex(indexName, "type", id);
    //    }
    //
    //    public UpdateRequestBuilder prepareUpdate(String indexName, String id) {
    //        return client.prepareUpdate(indexName, "type", id);
    //    }
    //
    //    public GetRequestBuilder prepareGet(String indexName, String id) {
    //        return client.prepareGet(indexName, "type", id);
    //    }

    public DocumentResult prepareGet(String index, String id) {
        Get get = new Get.Builder(index, id).build();
        try {
            return client.execute(get);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return null;
    }
    //
    //    public DeleteByQueryRequestBuilder prepareDelete() {
    //        return DeleteByQueryAction.INSTANCE.newRequestBuilder(client);
    //    }
    //
    //    public MultiGetRequestBuilder prepareMultiGet() {
    //        return client.prepareMultiGet();
    //    }
    //
    //    public BulkRequestBuilder prepareBulk() {
    //        return client.prepareBulk();
    //    }
    //
    //    public void update(UpdateRequest updateRequest) {
    //        try {
    //            client.update(updateRequest).get();
    //        } catch (InterruptedException | ExecutionException e) {
    //            logger.error(e.getMessage(), e);
    //        }
    //    }

    public boolean isExistsIndex(String name) {
        try {
            return client.execute(new IndicesExists.Builder(name).build()).isSucceeded();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return false;
    }

    public boolean prepareIndex(String table, String id,Map<String, Object> source,boolean refresh) {
        Index index = new Index.Builder(source)
                .index(table)
                .type("type")
                .id(id)
                .setParameter(Parameters.REFRESH, refresh)
                .build();
        try {
            JestResult result =  client.execute(index);
            return result.isSucceeded();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    public boolean prepareUpdate(String index, String id,String content) {
        try {
            JestResult result =  client.execute(new Update.Builder(content).index(index).id(id).build());
            return result.isSucceeded();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }
    
    public boolean prepareUpdate(String index, String id,Map<String, Object> content) {
        try {
            JestResult result =  client.execute(new Update.Builder(content).index(index).id(id).build());
            return result.isSucceeded();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    public boolean prepareDelete(String id, String index) {
        JestResult result = null;
        try {
            result = client.execute(new Delete.Builder(id)
                    .index(index)
                    .build());
            return result.isSucceeded();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return false;
    }
    
    public long batchDelete(String index, String query) {
        try {
            JestResult result =  client.execute(new DeleteByQuery.Builder(query).addIndex(index).build());
            return result.getResponseCode();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return 0;
    }
}
