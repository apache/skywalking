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

import java.util.*;
import org.apache.skywalking.apm.collector.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.apm.collector.core.util.*;
import org.apache.skywalking.apm.collector.storage.dao.ui.IInstanceUIDAO;
import org.apache.skywalking.apm.collector.storage.es.base.dao.EsDAO;
import org.apache.skywalking.apm.collector.storage.table.register.*;
import org.apache.skywalking.apm.collector.storage.ui.application.Application;
import org.apache.skywalking.apm.collector.storage.ui.server.AppServerInfo;
import org.elasticsearch.action.get.*;
import org.elasticsearch.action.search.*;
import org.elasticsearch.index.query.*;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.metrics.valuecount.ValueCount;
import org.elasticsearch.search.sort.*;
import org.slf4j.*;

/**
 * @author peng-yongsheng
 */
public class InstanceEsUIDAO extends EsDAO implements IInstanceUIDAO {

    private final Logger logger = LoggerFactory.getLogger(InstanceEsUIDAO.class);

    public InstanceEsUIDAO(ElasticSearchClient client) {
        super(client);
    }

    @Override public List<Application> getApplications(long startSecondTimeBucket, long endSecondTimeBucket,
        int... applicationIds) {
        logger.debug("application list get, start time: {}, end time: {}", startSecondTimeBucket, endSecondTimeBucket);
        SearchRequestBuilder searchRequestBuilder = getClient().prepareSearch(InstanceTable.TABLE);
        searchRequestBuilder.setTypes(InstanceTable.TABLE_TYPE);
        searchRequestBuilder.setSearchType(SearchType.DFS_QUERY_THEN_FETCH);

        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();

        BoolQueryBuilder boolQuery1 = QueryBuilders.boolQuery();
        boolQuery1.must().add(QueryBuilders.rangeQuery(InstanceTable.HEARTBEAT_TIME.getName()).gte(endSecondTimeBucket));
        boolQuery1.must().add(QueryBuilders.rangeQuery(InstanceTable.REGISTER_TIME.getName()).lte(endSecondTimeBucket));

        BoolQueryBuilder boolQuery2 = QueryBuilders.boolQuery();
        boolQuery2.must().add(QueryBuilders.rangeQuery(InstanceTable.REGISTER_TIME.getName()).lte(endSecondTimeBucket));
        boolQuery2.must().add(QueryBuilders.rangeQuery(InstanceTable.HEARTBEAT_TIME.getName()).gte(startSecondTimeBucket));

        BoolQueryBuilder timeBoolQuery = QueryBuilders.boolQuery();
        timeBoolQuery.should().add(boolQuery1);
        timeBoolQuery.should().add(boolQuery2);

        boolQueryBuilder.must().add(timeBoolQuery);

        boolQueryBuilder.must().add(QueryBuilders.termQuery(InstanceTable.IS_ADDRESS.getName(), BooleanUtils.FALSE));
        if (applicationIds.length > 0) {
            boolQueryBuilder.must().add(QueryBuilders.termsQuery(InstanceTable.APPLICATION_ID.getName(), applicationIds));
        }

        searchRequestBuilder.setQuery(boolQueryBuilder);
        searchRequestBuilder.setSize(0);
        searchRequestBuilder.addAggregation(AggregationBuilders.terms(InstanceTable.APPLICATION_ID.getName()).field(InstanceTable.APPLICATION_ID.getName()).size(100)
            .subAggregation(AggregationBuilders.count(InstanceTable.INSTANCE_ID.getName()).field(InstanceTable.INSTANCE_ID.getName())));

        SearchResponse searchResponse = searchRequestBuilder.execute().actionGet();
        Terms genders = searchResponse.getAggregations().get(InstanceTable.APPLICATION_ID.getName());

        List<Application> applications = new LinkedList<>();
        for (Terms.Bucket applicationsBucket : genders.getBuckets()) {
            Integer applicationId = applicationsBucket.getKeyAsNumber().intValue();
            logger.debug("applicationId: {}", applicationId);

            ValueCount instanceCount = applicationsBucket.getAggregations().get(InstanceTable.INSTANCE_ID.getName());

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
            instance.setApplicationId(((Number)getResponse.getSource().get(InstanceTable.APPLICATION_ID.getName())).intValue());
            instance.setAgentUUID((String)getResponse.getSource().get(InstanceTable.AGENT_UUID.getName()));
            instance.setRegisterTime(((Number)getResponse.getSource().get(InstanceTable.REGISTER_TIME.getName())).longValue());
            instance.setHeartBeatTime(((Number)getResponse.getSource().get(InstanceTable.HEARTBEAT_TIME.getName())).longValue());
            instance.setOsInfo((String)getResponse.getSource().get(InstanceTable.OS_INFO.getName()));
            return instance;
        }
        return null;
    }

    @Override
    public List<AppServerInfo> searchServer(String keyword, long startSecondTimeBucket, long endSecondTimeBucket) {
        logger.debug("get instances info, keyword: {}, startSecondTimeBucket: {}, endSecondTimeBucket: {}", keyword, startSecondTimeBucket, endSecondTimeBucket);
        SearchRequestBuilder searchRequestBuilder = getClient().prepareSearch(InstanceTable.TABLE);
        searchRequestBuilder.setTypes(InstanceTable.TABLE_TYPE);
        searchRequestBuilder.setSearchType(SearchType.DFS_QUERY_THEN_FETCH);
        searchRequestBuilder.setSize(1000);

        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
        if (StringUtils.isNotEmpty(keyword)) {
            boolQuery.must().add(QueryBuilders.queryStringQuery(keyword));
        }
        boolQuery.must().add(QueryBuilders.termQuery(InstanceTable.IS_ADDRESS.getName(), BooleanUtils.FALSE));

        BoolQueryBuilder boolQuery1 = QueryBuilders.boolQuery();
        boolQuery1.must().add(QueryBuilders.rangeQuery(InstanceTable.HEARTBEAT_TIME.getName()).gte(endSecondTimeBucket));
        boolQuery1.must().add(QueryBuilders.rangeQuery(InstanceTable.REGISTER_TIME.getName()).lte(endSecondTimeBucket));

        BoolQueryBuilder boolQuery2 = QueryBuilders.boolQuery();
        boolQuery2.must().add(QueryBuilders.rangeQuery(InstanceTable.REGISTER_TIME.getName()).lte(endSecondTimeBucket));
        boolQuery2.must().add(QueryBuilders.rangeQuery(InstanceTable.HEARTBEAT_TIME.getName()).gte(startSecondTimeBucket));

        BoolQueryBuilder timeBoolQuery = QueryBuilders.boolQuery();
        timeBoolQuery.should().add(boolQuery1);
        timeBoolQuery.should().add(boolQuery2);

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
        boolQuery.filter().add(QueryBuilders.termQuery(InstanceTable.APPLICATION_ID.getName(), applicationId));
        boolQuery.filter().add(QueryBuilders.termQuery(InstanceTable.IS_ADDRESS.getName(), BooleanUtils.FALSE));

        BoolQueryBuilder boolQuery1 = QueryBuilders.boolQuery();
        boolQuery1.filter().add(QueryBuilders.rangeQuery(InstanceTable.HEARTBEAT_TIME.getName()).gte(endSecondTimeBucket));
        boolQuery1.filter().add(QueryBuilders.rangeQuery(InstanceTable.REGISTER_TIME.getName()).lte(endSecondTimeBucket));

        BoolQueryBuilder boolQuery2 = QueryBuilders.boolQuery();
        boolQuery2.filter().add(QueryBuilders.rangeQuery(InstanceTable.REGISTER_TIME.getName()).lte(endSecondTimeBucket));
        boolQuery2.filter().add(QueryBuilders.rangeQuery(InstanceTable.HEARTBEAT_TIME.getName()).gte(startSecondTimeBucket));

        BoolQueryBuilder timeBoolQuery = QueryBuilders.boolQuery();
        timeBoolQuery.should().add(boolQuery1);
        timeBoolQuery.should().add(boolQuery2);

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

        searchRequestBuilder.setQuery(QueryBuilders.termQuery(InstanceTable.APPLICATION_ID.getName(), applicationId));
        searchRequestBuilder.addSort(SortBuilders.fieldSort(InstanceTable.REGISTER_TIME.getName()).order(SortOrder.ASC));

        SearchResponse searchResponse = searchRequestBuilder.execute().actionGet();

        SearchHit[] searchHits = searchResponse.getHits().getHits();

        if (searchHits.length > 0) {
            return ((Number)searchHits[0].getSource().get(InstanceTable.REGISTER_TIME.getName())).longValue();
        }

        return Long.MIN_VALUE;
    }

    @Override public long getLatestHeartBeatTime(int applicationId) {
        SearchRequestBuilder searchRequestBuilder = getClient().prepareSearch(InstanceTable.TABLE);
        searchRequestBuilder.setTypes(InstanceTable.TABLE_TYPE);
        searchRequestBuilder.setSearchType(SearchType.DFS_QUERY_THEN_FETCH);
        searchRequestBuilder.setSize(1);

        searchRequestBuilder.setQuery(QueryBuilders.termQuery(InstanceTable.APPLICATION_ID.getName(), applicationId));
        searchRequestBuilder.addSort(SortBuilders.fieldSort(InstanceTable.HEARTBEAT_TIME.getName()).order(SortOrder.DESC));

        SearchResponse searchResponse = searchRequestBuilder.execute().actionGet();

        SearchHit[] searchHits = searchResponse.getHits().getHits();

        if (searchHits.length > 0) {
            return ((Number)searchHits[0].getSource().get(InstanceTable.HEARTBEAT_TIME.getName())).longValue();
        }

        return Long.MAX_VALUE;
    }

    private List<AppServerInfo> buildAppServerInfo(SearchHit[] searchHits) {
        List<AppServerInfo> appServerInfos = new LinkedList<>();
        for (SearchHit searchHit : searchHits) {
            AppServerInfo appServerInfo = new AppServerInfo();
            appServerInfo.setId(((Number)searchHit.getSource().get(InstanceTable.INSTANCE_ID.getName())).intValue());
            appServerInfo.setApplicationId(((Number)searchHit.getSource().get(InstanceTable.APPLICATION_ID.getName())).intValue());
            appServerInfo.setOsInfo((String)searchHit.getSource().get(InstanceTable.OS_INFO.getName()));
            appServerInfos.add(appServerInfo);
        }
        return appServerInfos;
    }
}
