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


package org.apache.skywalking.apm.collector.storage.es.dao;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.LinkedList;
import java.util.List;
import org.apache.skywalking.apm.collector.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.apm.collector.storage.es.base.dao.EsDAO;
import org.apache.skywalking.apm.collector.storage.table.register.Instance;
import org.apache.skywalking.apm.collector.storage.table.register.InstanceTable;
import org.elasticsearch.action.get.GetRequestBuilder;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.index.query.AbstractQueryBuilder;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.metrics.valuecount.ValueCount;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortMode;
import org.apache.skywalking.apm.collector.core.util.TimeBucketUtils;
import org.apache.skywalking.apm.collector.storage.dao.IInstanceUIDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class InstanceEsUIDAO extends EsDAO implements IInstanceUIDAO {

    private final Logger logger = LoggerFactory.getLogger(InstanceEsUIDAO.class);

    public InstanceEsUIDAO(ElasticSearchClient client) {
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
        SearchRequestBuilder searchRequestBuilder = getClient().prepareSearch(InstanceTable.TABLE);
        searchRequestBuilder.setTypes(InstanceTable.TABLE_TYPE);
        searchRequestBuilder.setSearchType(SearchType.DFS_QUERY_THEN_FETCH);
        searchRequestBuilder.setQuery(queryBuilder);
        searchRequestBuilder.setSize(1);
        searchRequestBuilder.setFetchSource(InstanceTable.COLUMN_HEARTBEAT_TIME, null);
        searchRequestBuilder.addSort(SortBuilders.fieldSort(InstanceTable.COLUMN_HEARTBEAT_TIME).sortMode(SortMode.MAX));

        SearchResponse searchResponse = searchRequestBuilder.execute().actionGet();
        SearchHit[] searchHits = searchResponse.getHits().getHits();

        Long heartBeatTime = 0L;
        for (SearchHit searchHit : searchHits) {
            heartBeatTime = (Long)searchHit.getSource().get(InstanceTable.COLUMN_HEARTBEAT_TIME);
            logger.debug("heartBeatTime: {}", heartBeatTime);
            heartBeatTime = heartBeatTime - 5;
        }
        return heartBeatTime;
    }

    @Override public JsonArray getApplications(long startTime, long endTime) {
        logger.debug("application list get, start time: {}, end time: {}", startTime, endTime);
        SearchRequestBuilder searchRequestBuilder = getClient().prepareSearch(InstanceTable.TABLE);
        searchRequestBuilder.setTypes(InstanceTable.TABLE_TYPE);
        searchRequestBuilder.setSearchType(SearchType.DFS_QUERY_THEN_FETCH);
        searchRequestBuilder.setQuery(QueryBuilders.rangeQuery(InstanceTable.COLUMN_HEARTBEAT_TIME).gte(startTime));
        searchRequestBuilder.setSize(0);
        searchRequestBuilder.addAggregation(AggregationBuilders.terms(InstanceTable.COLUMN_APPLICATION_ID).field(InstanceTable.COLUMN_APPLICATION_ID).size(100)
            .subAggregation(AggregationBuilders.count(InstanceTable.COLUMN_INSTANCE_ID).field(InstanceTable.COLUMN_INSTANCE_ID)));

        SearchResponse searchResponse = searchRequestBuilder.execute().actionGet();
        Terms genders = searchResponse.getAggregations().get(InstanceTable.COLUMN_APPLICATION_ID);

        JsonArray applications = new JsonArray();
        for (Terms.Bucket applicationsBucket : genders.getBuckets()) {
            Integer applicationId = applicationsBucket.getKeyAsNumber().intValue();
            logger.debug("applicationId: {}", applicationId);

            ValueCount instanceCount = applicationsBucket.getAggregations().get(InstanceTable.COLUMN_INSTANCE_ID);

            JsonObject application = new JsonObject();
            application.addProperty("applicationId", applicationId);
            application.addProperty("instanceCount", instanceCount.getValue());
            applications.add(application);
        }
        return applications;
    }

    @Override public Instance getInstance(int instanceId) {
        logger.debug("get instance info, instance id: {}", instanceId);
        GetRequestBuilder requestBuilder = getClient().prepareGet(InstanceTable.TABLE, String.valueOf(instanceId));
        GetResponse getResponse = requestBuilder.get();
        if (getResponse.isExists()) {
            Instance instance = new Instance(getResponse.getId());
            instance.setApplicationId(((Number)getResponse.getSource().get(InstanceTable.COLUMN_APPLICATION_ID)).intValue());
            instance.setAgentUUID((String)getResponse.getSource().get(InstanceTable.COLUMN_AGENT_UUID));
            instance.setRegisterTime(((Number)getResponse.getSource().get(InstanceTable.COLUMN_REGISTER_TIME)).longValue());
            instance.setHeartBeatTime(((Number)getResponse.getSource().get(InstanceTable.COLUMN_HEARTBEAT_TIME)).longValue());
            instance.setOsInfo((String)getResponse.getSource().get(InstanceTable.COLUMN_OS_INFO));
            return instance;
        }
        return null;
    }

    @Override public List<Instance> getInstances(int applicationId, long timeBucket) {
        logger.debug("get instances info, application id: {}, timeBucket: {}", applicationId, timeBucket);
        SearchRequestBuilder searchRequestBuilder = getClient().prepareSearch(InstanceTable.TABLE);
        searchRequestBuilder.setTypes(InstanceTable.TABLE_TYPE);
        searchRequestBuilder.setSearchType(SearchType.DFS_QUERY_THEN_FETCH);
        searchRequestBuilder.setSize(1000);

        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
        boolQuery.must().add(QueryBuilders.rangeQuery(InstanceTable.COLUMN_HEARTBEAT_TIME).gte(timeBucket));
        boolQuery.must().add(QueryBuilders.termQuery(InstanceTable.COLUMN_APPLICATION_ID, applicationId));
        searchRequestBuilder.setQuery(boolQuery);

        SearchResponse searchResponse = searchRequestBuilder.execute().actionGet();
        SearchHit[] searchHits = searchResponse.getHits().getHits();

        List<Instance> instanceList = new LinkedList<>();
        for (SearchHit searchHit : searchHits) {
            Instance instance = new Instance(searchHit.getId());
            instance.setApplicationId(((Number)searchHit.getSource().get(InstanceTable.COLUMN_APPLICATION_ID)).intValue());
            instance.setHeartBeatTime(((Number)searchHit.getSource().get(InstanceTable.COLUMN_HEARTBEAT_TIME)).longValue());
            instance.setInstanceId(((Number)searchHit.getSource().get(InstanceTable.COLUMN_INSTANCE_ID)).intValue());
            instanceList.add(instance);
        }
        return instanceList;
    }
}
