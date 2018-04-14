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
import org.apache.skywalking.apm.collector.storage.dao.ui.IApplicationAlarmListUIDAO;
import org.apache.skywalking.apm.collector.storage.es.base.dao.EsDAO;
import org.apache.skywalking.apm.collector.storage.table.alarm.ApplicationAlarmListTable;
import org.apache.skywalking.apm.collector.storage.ui.common.Step;
import org.apache.skywalking.apm.collector.storage.utils.TimePyramidTableNameBuilder;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;

/**
 * @author peng-yongsheng
 */
public class ApplicationAlarmListEsUIDAO extends EsDAO implements IApplicationAlarmListUIDAO {

    public ApplicationAlarmListEsUIDAO(ElasticSearchClient client) {
        super(client);
    }

    @Override public List<AlarmTrend> getAlarmedApplicationNum(Step step, long startTimeBucket, long endTimeBucket) {
        String tableName = TimePyramidTableNameBuilder.build(step, ApplicationAlarmListTable.TABLE);
        SearchRequestBuilder searchRequestBuilder = getClient().prepareSearch(tableName);
        searchRequestBuilder.setTypes(ApplicationAlarmListTable.TABLE_TYPE);
        searchRequestBuilder.setSearchType(SearchType.DFS_QUERY_THEN_FETCH);
        searchRequestBuilder.setQuery(QueryBuilders.rangeQuery(ApplicationAlarmListTable.TIME_BUCKET.getName()).gte(startTimeBucket).lte(endTimeBucket));
        searchRequestBuilder.setSize(0);

        searchRequestBuilder.addAggregation(AggregationBuilders.terms(ApplicationAlarmListTable.TIME_BUCKET.getName()).field(ApplicationAlarmListTable.TIME_BUCKET.getName()).size(100)
            .subAggregation(AggregationBuilders.terms(ApplicationAlarmListTable.APPLICATION_ID.getName()).field(ApplicationAlarmListTable.APPLICATION_ID.getName()).size(100)));

        SearchResponse searchResponse = searchRequestBuilder.execute().actionGet();

        Terms timeBucketTerms = searchResponse.getAggregations().get(ApplicationAlarmListTable.TIME_BUCKET.getName());

        List<AlarmTrend> alarmTrends = new LinkedList<>();
        for (Terms.Bucket timeBucketBucket : timeBucketTerms.getBuckets()) {
            Terms applicationBucketTerms = timeBucketBucket.getAggregations().get(ApplicationAlarmListTable.APPLICATION_ID.getName());

            AlarmTrend alarmTrend = new AlarmTrend();
            alarmTrend.setNumberOfApplication(applicationBucketTerms.getBuckets().size());
            alarmTrend.setTimeBucket(timeBucketBucket.getKeyAsNumber().longValue());
            alarmTrends.add(alarmTrend);
        }

        return alarmTrends;
    }
}
