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

package org.apache.skywalking.apm.collector.storage.es.dao.ui;

import java.util.LinkedList;
import java.util.List;
import org.apache.skywalking.apm.collector.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.apm.collector.core.util.BooleanUtils;
import org.apache.skywalking.apm.collector.core.util.StringUtils;
import org.apache.skywalking.apm.collector.core.util.TimeBucketUtils;
import org.apache.skywalking.apm.collector.storage.dao.ui.IInstanceUIDAO;
import org.apache.skywalking.apm.collector.storage.es.base.dao.EsDAO;
import org.apache.skywalking.apm.collector.storage.table.register.Instance;
import org.apache.skywalking.apm.collector.storage.table.register.InstanceTable;
import org.apache.skywalking.apm.collector.storage.ui.application.Application;
import org.apache.skywalking.apm.collector.storage.ui.server.AppServerInfo;
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

    @Override public List<Application> getApplications(long startSecondTimeBucket, long endSecondTimeBucket,
        int... applicationIds) {
        logger.debug("application list get, start time: {}, end time: {}", startSecondTimeBucket, endSecondTimeBucket);
        SearchRequestBuilder searchRequestBuilder = getClient().prepareSearch(InstanceTable.TABLE);
        searchRequestBuilder.setTypes(InstanceTable.TABLE_TYPE);
        searchRequestBuilder.setSearchType(SearchType.DFS_QUERY_THEN_FETCH);

        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();

        //TODO second
        BoolQueryBuilder timeBoolQuery = QueryBuilders.boolQuery();
        timeBoolQuery.should().add(QueryBuilders.rangeQuery(InstanceTable.COLUMN_REGISTER_TIME).gte(startSecondTimeBucket).lte(endSecondTimeBucket));
        timeBoolQuery.should().add(QueryBuilders.rangeQuery(InstanceTable.COLUMN_HEARTBEAT_TIME).gte(startSecondTimeBucket).lte(endSecondTimeBucket));

        boolQueryBuilder.must().add(timeBoolQuery);

        boolQueryBuilder.must().add(QueryBuilders.termQuery(InstanceTable.COLUMN_IS_ADDRESS, BooleanUtils.FALSE));
        if (applicationIds.length > 0) {
            boolQueryBuilder.must().add(QueryBuilders.termsQuery(InstanceTable.COLUMN_APPLICATION_ID, applicationIds));
        }

        searchRequestBuilder.setQuery(boolQueryBuilder);
        searchRequestBuilder.setSize(0);
        searchRequestBuilder.addAggregation(AggregationBuilders.terms(InstanceTable.COLUMN_APPLICATION_ID).field(InstanceTable.COLUMN_APPLICATION_ID).size(100)
            .subAggregation(AggregationBuilders.count(InstanceTable.COLUMN_INSTANCE_ID).field(InstanceTable.COLUMN_INSTANCE_ID)));

        SearchResponse searchResponse = searchRequestBuilder.execute().actionGet();
        Terms genders = searchResponse.getAggregations().get(InstanceTable.COLUMN_APPLICATION_ID);

        List<Application> applications = new LinkedList<>();
        for (Terms.Bucket applicationsBucket : genders.getBuckets()) {
            Integer applicationId = applicationsBucket.getKeyAsNumber().intValue();
            logger.debug("applicationId: {}", applicationId);

            ValueCount instanceCount = applicationsBucket.getAggregations().get(InstanceTable.COLUMN_INSTANCE_ID);

            Application application = new Application();
            application.setId(applicationId);
            application.setNumOfServer((int)instanceCount.getValue());
            applications.add(application);
        }
        return applications;
    }

    @Override public Instance getInstance(int instanceId) {
        logger.debug("get instance info, instance id: {}", instanceId);
        GetRequestBuilder requestBuilder = getClient().prepareGet(InstanceTable.TABLE, String.valueOf(instanceId));
        GetResponse getResponse = requestBuilder.get();
        if (getResponse.isExists()) {
            Instance instance = new Instance();
            instance.setId(getResponse.getId());
            instance.setApplicationId(((Number)getResponse.getSource().get(InstanceTable.COLUMN_APPLICATION_ID)).intValue());
            instance.setAgentUUID((String)getResponse.getSource().get(InstanceTable.COLUMN_AGENT_UUID));
            instance.setRegisterTime(((Number)getResponse.getSource().get(InstanceTable.COLUMN_REGISTER_TIME)).longValue());
            instance.setHeartBeatTime(((Number)getResponse.getSource().get(InstanceTable.COLUMN_HEARTBEAT_TIME)).longValue());
            instance.setOsInfo((String)getResponse.getSource().get(InstanceTable.COLUMN_OS_INFO));
            return instance;
        }
        return null;
    }

    @Override
    public List<AppServerInfo> searchServer(String keyword, long startSecondTimeBucket, long endSecondTimeBucket) {
        logger.debug("get instances info, keyword: {}, start: {}, end: {}", keyword, startSecondTimeBucket, endSecondTimeBucket);
        SearchRequestBuilder searchRequestBuilder = getClient().prepareSearch(InstanceTable.TABLE);
        searchRequestBuilder.setTypes(InstanceTable.TABLE_TYPE);
        searchRequestBuilder.setSearchType(SearchType.DFS_QUERY_THEN_FETCH);
        searchRequestBuilder.setSize(1000);

        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
        if (StringUtils.isNotEmpty(keyword)) {
            boolQuery.must().add(QueryBuilders.queryStringQuery(keyword));
        }
        boolQuery.must().add(QueryBuilders.termQuery(InstanceTable.COLUMN_IS_ADDRESS, BooleanUtils.FALSE));

        //TODO second
        BoolQueryBuilder timeBoolQuery = QueryBuilders.boolQuery();
        timeBoolQuery.should().add(QueryBuilders.rangeQuery(InstanceTable.COLUMN_REGISTER_TIME).gte(startSecondTimeBucket).lte(endSecondTimeBucket));
        timeBoolQuery.should().add(QueryBuilders.rangeQuery(InstanceTable.COLUMN_HEARTBEAT_TIME).gte(startSecondTimeBucket).lte(endSecondTimeBucket));

        boolQuery.must().add(timeBoolQuery);

        searchRequestBuilder.setQuery(boolQuery);

        SearchResponse searchResponse = searchRequestBuilder.execute().actionGet();
        SearchHit[] searchHits = searchResponse.getHits().getHits();

        return buildAppServerInfo(searchHits);
    }

    @Override
    public List<AppServerInfo> getAllServer(int applicationId, long startSecondTimeBucket, long endSecondTimeBucket) {
        logger.debug("get instances info, applicationId: {}, start: {}, end: {}", applicationId, startSecondTimeBucket, endSecondTimeBucket);
        SearchRequestBuilder searchRequestBuilder = getClient().prepareSearch(InstanceTable.TABLE);
        searchRequestBuilder.setTypes(InstanceTable.TABLE_TYPE);
        searchRequestBuilder.setSearchType(SearchType.DFS_QUERY_THEN_FETCH);
        searchRequestBuilder.setSize(1000);

        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
        boolQuery.must().add(QueryBuilders.termQuery(InstanceTable.COLUMN_APPLICATION_ID, applicationId));
        boolQuery.must().add(QueryBuilders.termQuery(InstanceTable.COLUMN_IS_ADDRESS, BooleanUtils.FALSE));

        //TODO second
        BoolQueryBuilder timeBoolQuery = QueryBuilders.boolQuery();
        timeBoolQuery.should().add(QueryBuilders.rangeQuery(InstanceTable.COLUMN_REGISTER_TIME).gte(startSecondTimeBucket).lte(endSecondTimeBucket));
        timeBoolQuery.should().add(QueryBuilders.rangeQuery(InstanceTable.COLUMN_HEARTBEAT_TIME).gte(startSecondTimeBucket).lte(endSecondTimeBucket));

        boolQuery.must().add(timeBoolQuery);
        searchRequestBuilder.setQuery(boolQuery);

        SearchResponse searchResponse = searchRequestBuilder.execute().actionGet();
        SearchHit[] searchHits = searchResponse.getHits().getHits();

        return buildAppServerInfo(searchHits);
    }

    @Override public long getEarliestRegisterTime(int applicationId) {
        SearchRequestBuilder searchRequestBuilder = getClient().prepareSearch(InstanceTable.TABLE);
        searchRequestBuilder.setTypes(InstanceTable.TABLE_TYPE);
        searchRequestBuilder.setSearchType(SearchType.DFS_QUERY_THEN_FETCH);
        searchRequestBuilder.setSize(1);

        searchRequestBuilder.setQuery(QueryBuilders.termQuery(InstanceTable.COLUMN_APPLICATION_ID, applicationId));
        searchRequestBuilder.addSort(SortBuilders.fieldSort(InstanceTable.COLUMN_REGISTER_TIME).sortMode(SortMode.MIN));

        SearchResponse searchResponse = searchRequestBuilder.execute().actionGet();

        SearchHit[] searchHits = searchResponse.getHits().getHits();

        if (searchHits.length > 0) {
            return ((Number)searchHits[0].getSource().get(InstanceTable.COLUMN_REGISTER_TIME)).longValue();
        }

        return Long.MIN_VALUE;
    }

    @Override public long getLatestHeartBeatTime(int applicationId) {
        SearchRequestBuilder searchRequestBuilder = getClient().prepareSearch(InstanceTable.TABLE);
        searchRequestBuilder.setTypes(InstanceTable.TABLE_TYPE);
        searchRequestBuilder.setSearchType(SearchType.DFS_QUERY_THEN_FETCH);
        searchRequestBuilder.setSize(1);

        searchRequestBuilder.setQuery(QueryBuilders.termQuery(InstanceTable.COLUMN_APPLICATION_ID, applicationId));
        searchRequestBuilder.addSort(SortBuilders.fieldSort(InstanceTable.COLUMN_HEARTBEAT_TIME).sortMode(SortMode.MAX));

        SearchResponse searchResponse = searchRequestBuilder.execute().actionGet();

        SearchHit[] searchHits = searchResponse.getHits().getHits();

        if (searchHits.length > 0) {
            return ((Number)searchHits[0].getSource().get(InstanceTable.COLUMN_HEARTBEAT_TIME)).longValue();
        }

        return Long.MAX_VALUE;
    }

    private List<AppServerInfo> buildAppServerInfo(SearchHit[] searchHits) {
        List<AppServerInfo> appServerInfos = new LinkedList<>();
        for (SearchHit searchHit : searchHits) {
            AppServerInfo appServerInfo = new AppServerInfo();
            appServerInfo.setId(((Number)searchHit.getSource().get(InstanceTable.COLUMN_INSTANCE_ID)).intValue());
            appServerInfo.setApplicationId(((Number)searchHit.getSource().get(InstanceTable.COLUMN_APPLICATION_ID)).intValue());
            appServerInfo.setOsInfo((String)searchHit.getSource().get(InstanceTable.COLUMN_OS_INFO));
            appServerInfos.add(appServerInfo);
        }
        return appServerInfos;
    }
}
