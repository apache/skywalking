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
import org.apache.skywalking.apm.collector.core.util.Const;
import org.apache.skywalking.apm.collector.storage.es.base.dao.EsDAO;
import org.apache.skywalking.apm.collector.storage.table.jvm.GCMetricTable;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.get.MultiGetItemResponse;
import org.elasticsearch.action.get.MultiGetRequestBuilder;
import org.elasticsearch.action.get.MultiGetResponse;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.metrics.sum.Sum;
import org.apache.skywalking.apm.collector.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.apm.collector.core.util.TimeBucketUtils;
import org.apache.skywalking.apm.collector.storage.dao.IGCMetricUIDAO;
import org.apache.skywalking.apm.network.proto.GCPhrase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class GCMetricEsUIDAO extends EsDAO implements IGCMetricUIDAO {

    private final Logger logger = LoggerFactory.getLogger(GCMetricEsUIDAO.class);

    public GCMetricEsUIDAO(ElasticSearchClient client) {
        super(client);
    }

    @Override public GCCount getGCCount(long[] timeBuckets, int instanceId) {
        logger.debug("get gc count, timeBuckets: {}, instanceId: {}", timeBuckets, instanceId);
        SearchRequestBuilder searchRequestBuilder = getClient().prepareSearch(GCMetricTable.TABLE);
        searchRequestBuilder.setTypes(GCMetricTable.TABLE_TYPE);
        searchRequestBuilder.setSearchType(SearchType.DFS_QUERY_THEN_FETCH);

        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
        boolQuery.must().add(QueryBuilders.termQuery(GCMetricTable.COLUMN_INSTANCE_ID, instanceId));
        boolQuery.must().add(QueryBuilders.termsQuery(GCMetricTable.COLUMN_TIME_BUCKET, timeBuckets));

        searchRequestBuilder.setQuery(boolQuery);
        searchRequestBuilder.setSize(0);
        searchRequestBuilder.addAggregation(
            AggregationBuilders.terms(GCMetricTable.COLUMN_PHRASE).field(GCMetricTable.COLUMN_PHRASE)
                .subAggregation(AggregationBuilders.sum(GCMetricTable.COLUMN_COUNT).field(GCMetricTable.COLUMN_COUNT)));

        SearchResponse searchResponse = searchRequestBuilder.execute().actionGet();

        GCCount gcCount = new GCCount();
        Terms phraseAggregation = searchResponse.getAggregations().get(GCMetricTable.COLUMN_PHRASE);
        for (Terms.Bucket phraseBucket : phraseAggregation.getBuckets()) {
            int phrase = phraseBucket.getKeyAsNumber().intValue();
            Sum sumAggregation = phraseBucket.getAggregations().get(GCMetricTable.COLUMN_COUNT);
            int count = (int)sumAggregation.getValue();

            if (phrase == GCPhrase.NEW_VALUE) {
                gcCount.setYoung(count);
            } else if (phrase == GCPhrase.OLD_VALUE) {
                gcCount.setOld(count);
            }
        }

        return gcCount;
    }

    @Override public JsonObject getMetric(int instanceId, long timeBucket) {
        JsonObject response = new JsonObject();

        String youngId = timeBucket + Const.ID_SPLIT + GCPhrase.NEW_VALUE + instanceId;
        GetResponse youngResponse = getClient().prepareGet(GCMetricTable.TABLE, youngId).get();
        if (youngResponse.isExists()) {
            response.addProperty("ygc", ((Number)youngResponse.getSource().get(GCMetricTable.COLUMN_COUNT)).intValue());
        }

        String oldId = timeBucket + Const.ID_SPLIT + GCPhrase.OLD_VALUE + instanceId;
        GetResponse oldResponse = getClient().prepareGet(GCMetricTable.TABLE, oldId).get();
        if (oldResponse.isExists()) {
            response.addProperty("ogc", ((Number)oldResponse.getSource().get(GCMetricTable.COLUMN_COUNT)).intValue());
        }

        return response;
    }

    @Override public JsonObject getMetric(int instanceId, long startTimeBucket, long endTimeBucket) {
        JsonObject response = new JsonObject();

        MultiGetRequestBuilder youngPrepareMultiGet = getClient().prepareMultiGet();
        long timeBucket = startTimeBucket;
        do {
            timeBucket = TimeBucketUtils.INSTANCE.addSecondForSecondTimeBucket(TimeBucketUtils.TimeBucketType.SECOND.name(), timeBucket, 1);
            String youngId = timeBucket + Const.ID_SPLIT + instanceId + Const.ID_SPLIT + GCPhrase.NEW_VALUE;
            youngPrepareMultiGet.add(GCMetricTable.TABLE, GCMetricTable.TABLE_TYPE, youngId);
        }
        while (timeBucket <= endTimeBucket);

        JsonArray youngArray = new JsonArray();
        MultiGetResponse multiGetResponse = youngPrepareMultiGet.get();
        for (MultiGetItemResponse itemResponse : multiGetResponse.getResponses()) {
            if (itemResponse.getResponse().isExists()) {
                youngArray.add(((Number)itemResponse.getResponse().getSource().get(GCMetricTable.COLUMN_COUNT)).intValue());
            } else {
                youngArray.add(0);
            }
        }
        response.add("ygc", youngArray);

        MultiGetRequestBuilder oldPrepareMultiGet = getClient().prepareMultiGet();
        timeBucket = startTimeBucket;
        do {
            timeBucket = TimeBucketUtils.INSTANCE.addSecondForSecondTimeBucket(TimeBucketUtils.TimeBucketType.SECOND.name(), timeBucket, 1);
            String oldId = timeBucket + Const.ID_SPLIT + instanceId + Const.ID_SPLIT + GCPhrase.OLD_VALUE;
            oldPrepareMultiGet.add(GCMetricTable.TABLE, GCMetricTable.TABLE_TYPE, oldId);
        }
        while (timeBucket <= endTimeBucket);

        JsonArray oldArray = new JsonArray();

        multiGetResponse = oldPrepareMultiGet.get();
        for (MultiGetItemResponse itemResponse : multiGetResponse.getResponses()) {
            if (itemResponse.getResponse().isExists()) {
                oldArray.add(((Number)itemResponse.getResponse().getSource().get(GCMetricTable.COLUMN_COUNT)).intValue());
            } else {
                oldArray.add(0);
            }
        }
        response.add("ogc", oldArray);

        return response;
    }
}
