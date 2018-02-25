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

package org.apache.skywalking.apm.collector.storage.es.http.dao.ui;

import java.util.LinkedList;
import java.util.List;

import org.apache.skywalking.apm.collector.client.elasticsearch.http.ElasticSearchHttpClient;
import org.apache.skywalking.apm.collector.core.util.BooleanUtils;
import org.apache.skywalking.apm.collector.core.util.StringUtils;
import org.apache.skywalking.apm.collector.core.util.TimeBucketUtils;
import org.apache.skywalking.apm.collector.storage.dao.ui.IInstanceUIDAO;
import org.apache.skywalking.apm.collector.storage.es.http.base.dao.EsHttpDAO;
import org.apache.skywalking.apm.collector.storage.table.register.Instance;
import org.apache.skywalking.apm.collector.storage.table.register.InstanceTable;
import org.apache.skywalking.apm.collector.storage.ui.application.Application;
import org.apache.skywalking.apm.collector.storage.ui.server.AppServerInfo;
import org.elasticsearch.index.query.AbstractQueryBuilder;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import io.searchbox.core.DocumentResult;
import io.searchbox.core.Search;
import io.searchbox.core.SearchResult;
import io.searchbox.core.search.aggregation.TermsAggregation;
import io.searchbox.core.search.aggregation.TermsAggregation.Entry;
import io.searchbox.core.search.aggregation.ValueCountAggregation;

/**
 * @author peng-yongsheng
 */
public class InstanceEsUIDAO extends EsHttpDAO implements IInstanceUIDAO {

    private final Logger logger = LoggerFactory.getLogger(InstanceEsUIDAO.class);

    public InstanceEsUIDAO(ElasticSearchHttpClient client) {
        super(client);
    }

    @Override public Long lastHeartBeatTime() {
        long fiveMinuteBefore = System.currentTimeMillis() - 5 * 60 * 1000;
        fiveMinuteBefore = TimeBucketUtils.INSTANCE.getSecondTimeBucket(fiveMinuteBefore);
        RangeQueryBuilder rangeQueryBuilder = QueryBuilders.rangeQuery(InstanceTable.COLUMN_HEARTBEAT_TIME).gt(fiveMinuteBefore);
        return heartBeatTime(rangeQueryBuilder);
    }

    @Override public Long instanceLastHeartBeatTime(long applicationInstanceId) {
        long fiveMinuteBefore = System.currentTimeMillis() - 5 * 60 * 1000;
        fiveMinuteBefore = TimeBucketUtils.INSTANCE.getSecondTimeBucket(fiveMinuteBefore);

        BoolQueryBuilder boolQueryBuilder = new BoolQueryBuilder();
        boolQueryBuilder.must(QueryBuilders.rangeQuery(InstanceTable.COLUMN_HEARTBEAT_TIME).gt(fiveMinuteBefore));
        boolQueryBuilder.must(QueryBuilders.termQuery(InstanceTable.COLUMN_INSTANCE_ID, applicationInstanceId));
        return heartBeatTime(boolQueryBuilder);
    }

    private Long heartBeatTime(AbstractQueryBuilder queryBuilder) {
//        SearchRequestBuilder searchRequestBuilder = getClient().prepareSearch(InstanceTable.TABLE);
//        searchRequestBuilder.setTypes(InstanceTable.TABLE_TYPE);
//        searchRequestBuilder.setSearchType(SearchType.DFS_QUERY_THEN_FETCH);
//        searchRequestBuilder.setQuery(queryBuilder);
//        searchRequestBuilder.setSize(1);
//        searchRequestBuilder.setFetchSource(InstanceTable.COLUMN_HEARTBEAT_TIME, null);
//        searchRequestBuilder.addSort(SortBuilders.fieldSort(InstanceTable.COLUMN_HEARTBEAT_TIME).sortMode(SortMode.MAX));
        
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.size(1);
        searchSourceBuilder.query(queryBuilder);
        searchSourceBuilder.fetchSource(InstanceTable.COLUMN_HEARTBEAT_TIME, null);
        searchSourceBuilder.sort(SortBuilders.fieldSort(InstanceTable.COLUMN_HEARTBEAT_TIME).sortMode(SortMode.MAX));
        
        Search search = new Search.Builder(searchSourceBuilder.toString())
                .addIndex(InstanceTable.TABLE)
                .addType(InstanceTable.TABLE_TYPE)
                .build();
        
        JsonArray result =  getClient().executeForJsonArray(search);

//        SearchResponse searchResponse = searchRequestBuilder.execute().actionGet();
//        SearchHit[] searchHits = searchResponse.getHits().getHits();

        Long heartBeatTime = 0L;
        for (JsonElement searchHit : result) {
            
            heartBeatTime = searchHit.getAsJsonObject().get(InstanceTable.COLUMN_HEARTBEAT_TIME).getAsLong();
            logger.debug("heartBeatTime: {}", heartBeatTime);
            heartBeatTime = heartBeatTime - 5;
        }
        return heartBeatTime;
    }

    @Override public List<Application> getApplications(long startTime, long endTime, int... applicationIds) {
        logger.debug("application list get, start time: {}, end time: {}", startTime, endTime);
//        SearchRequestBuilder searchRequestBuilder = getClient().prepareSearch(InstanceTable.TABLE);
//        searchRequestBuilder.setTypes(InstanceTable.TABLE_TYPE);
//        searchRequestBuilder.setSearchType(SearchType.DFS_QUERY_THEN_FETCH);

        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        boolQueryBuilder.must().add(QueryBuilders.rangeQuery(InstanceTable.COLUMN_HEARTBEAT_TIME).gte(startTime));
        boolQueryBuilder.must().add(QueryBuilders.termQuery(InstanceTable.COLUMN_IS_ADDRESS, BooleanUtils.FALSE));
        if (applicationIds.length > 0) {
            boolQueryBuilder.must().add(QueryBuilders.termsQuery(InstanceTable.COLUMN_APPLICATION_ID, applicationIds));
        }
        
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.query(boolQueryBuilder);
        searchSourceBuilder.aggregation(AggregationBuilders.terms(InstanceTable.COLUMN_APPLICATION_ID).field(InstanceTable.COLUMN_APPLICATION_ID).size(100)
            .subAggregation(AggregationBuilders.count(InstanceTable.COLUMN_INSTANCE_ID).field(InstanceTable.COLUMN_INSTANCE_ID)));
        searchSourceBuilder.size(0);

//        searchRequestBuilder.setQuery(boolQueryBuilder);
//        searchRequestBuilder.setSize(0);
//        searchRequestBuilder.addAggregation(AggregationBuilders.terms(InstanceTable.COLUMN_APPLICATION_ID).field(InstanceTable.COLUMN_APPLICATION_ID).size(100)
//            .subAggregation(AggregationBuilders.count(InstanceTable.COLUMN_INSTANCE_ID).field(InstanceTable.COLUMN_INSTANCE_ID)));

//        SearchResponse searchResponse = searchRequestBuilder.execute().actionGet();
//        Terms genders = searchResponse.getAggregations().get(InstanceTable.COLUMN_APPLICATION_ID);
        
        Search search = new Search.Builder(searchSourceBuilder.toString()).addIndex(InstanceTable.TABLE)
                .addType(InstanceTable.TABLE_TYPE)
                .build();
        
        SearchResult result =  getClient().execute(search);
        TermsAggregation terms = result.getAggregations().getTermsAggregation(InstanceTable.COLUMN_APPLICATION_ID);

        List<Application> applications = new LinkedList<>();
        for (Entry applicationsBucket : terms.getBuckets()) {
            
            Integer applicationId =  Integer.valueOf(applicationsBucket.getKeyAsString());
            logger.debug("applicationId: {}", applicationId);

            ValueCountAggregation instanceCount = applicationsBucket.getValueCountAggregation(InstanceTable.COLUMN_INSTANCE_ID);
            
            Application application = new Application();
            application.setId(applicationId);
            application.setNumOfServer(instanceCount.getValueCount().intValue());
            applications.add(application);
        }
        return applications;
    }

    @Override public Instance getInstance(int instanceId) {
        logger.debug("get instance info, instance id: {}", instanceId);
        DocumentResult getResponse = getClient().prepareGet(InstanceTable.TABLE, String.valueOf(instanceId));
        if (getResponse != null) {
            JsonObject source = getResponse.getJsonObject().getAsJsonObject("_source");
            Instance instance = new Instance();
            instance.setId(getResponse.getId());
            instance.setApplicationId((source.get(InstanceTable.COLUMN_APPLICATION_ID)).getAsInt());
            instance.setAgentUUID(source.get(InstanceTable.COLUMN_AGENT_UUID).getAsString());
            instance.setRegisterTime((source.get(InstanceTable.COLUMN_REGISTER_TIME)).getAsLong());
            instance.setHeartBeatTime((source.get(InstanceTable.COLUMN_HEARTBEAT_TIME)).getAsLong());
            instance.setOsInfo(source.get(InstanceTable.COLUMN_OS_INFO).getAsString());
            return instance;
        }
        return null;
    }

    @Override public List<AppServerInfo> searchServer(String keyword, long start, long end) {
        logger.debug("get instances info, keyword: {}, start: {}, end: {}", keyword, start, end);
//        SearchRequestBuilder searchRequestBuilder = getClient().prepareSearch(InstanceTable.TABLE);
//        searchRequestBuilder.setTypes(InstanceTable.TABLE_TYPE);
//        searchRequestBuilder.setSearchType(SearchType.DFS_QUERY_THEN_FETCH);
//        searchRequestBuilder.setSize(1000);
        
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.size(1000);
        

        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
        boolQuery.must().add(QueryBuilders.rangeQuery(InstanceTable.COLUMN_HEARTBEAT_TIME).gte(start).lte(end));
        if (StringUtils.isNotEmpty(keyword)) {
            boolQuery.must().add(QueryBuilders.queryStringQuery(keyword));
        }
        boolQuery.must().add(QueryBuilders.termQuery(InstanceTable.COLUMN_IS_ADDRESS, BooleanUtils.FALSE));
        searchSourceBuilder.query(boolQuery);
        
        Search search = new Search.Builder(searchSourceBuilder.toString())
                // multiple index or types can be added.
                .addIndex(InstanceTable.TABLE)
                .addType(InstanceTable.TABLE_TYPE)
                .build();
        
        SearchResult result =  getClient().execute(search);
        JsonArray array = result.getJsonObject().getAsJsonObject("hits").getAsJsonArray("hits");

        return buildAppServerInfo(array);
    }

    @Override public List<AppServerInfo> getAllServer(int applicationId, long start, long end) {
        logger.debug("get instances info, applicationId: {}, start: {}, end: {}", applicationId, start, end);
        
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.size(1000);

        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
        boolQuery.must().add(QueryBuilders.rangeQuery(InstanceTable.COLUMN_HEARTBEAT_TIME).gte(start).lte(end));
        boolQuery.must().add(QueryBuilders.termQuery(InstanceTable.COLUMN_APPLICATION_ID, applicationId));
        boolQuery.must().add(QueryBuilders.termQuery(InstanceTable.COLUMN_IS_ADDRESS, BooleanUtils.FALSE));
        
        searchSourceBuilder.query(boolQuery);
        
        Search search = new Search.Builder(searchSourceBuilder.toString())
                // multiple index or types can be added.
                .addIndex(InstanceTable.TABLE)
                .addType(InstanceTable.TABLE_TYPE)
                .build();


        SearchResult result =  getClient().execute(search);
        JsonArray array = result.getJsonObject().getAsJsonObject("hits").getAsJsonArray("hits");
        
        return buildAppServerInfo(array);
    }

    private List<AppServerInfo> buildAppServerInfo(JsonArray searchHits) {
        List<AppServerInfo> appServerInfos = new LinkedList<>();
        for (Object o : searchHits) {
            JsonObject searchHit = (JsonObject) o;
            JsonObject source = searchHit.getAsJsonObject("_source");
            AppServerInfo appServerInfo = new AppServerInfo();
            appServerInfo.setId((source.get(InstanceTable.COLUMN_INSTANCE_ID)).getAsInt() );
            appServerInfo.setOsInfo(source.get(InstanceTable.COLUMN_OS_INFO).getAsString());
            appServerInfos.add(appServerInfo);
        }
        return appServerInfos;
    }
}
