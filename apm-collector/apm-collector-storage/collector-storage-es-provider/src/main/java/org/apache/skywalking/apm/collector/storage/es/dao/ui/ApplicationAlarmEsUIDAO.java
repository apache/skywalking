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

import java.text.ParseException;
import org.apache.skywalking.apm.collector.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.apm.collector.core.util.StringUtils;
import org.apache.skywalking.apm.collector.core.util.TimeBucketUtils;
import org.apache.skywalking.apm.collector.storage.dao.ui.IApplicationAlarmUIDAO;
import org.apache.skywalking.apm.collector.storage.es.base.dao.EsDAO;
import org.apache.skywalking.apm.collector.storage.table.alarm.ApplicationAlarmTable;
import org.apache.skywalking.apm.collector.storage.ui.alarm.Alarm;
import org.apache.skywalking.apm.collector.storage.ui.alarm.AlarmItem;
import org.apache.skywalking.apm.collector.storage.ui.alarm.AlarmType;
import org.apache.skywalking.apm.collector.storage.ui.alarm.CauseType;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;

/**
 * @author peng-yongsheng
 */
public class ApplicationAlarmEsUIDAO extends EsDAO implements IApplicationAlarmUIDAO {

    public ApplicationAlarmEsUIDAO(ElasticSearchClient client) {
        super(client);
    }

    @Override
    public Alarm loadAlarmList(String keyword, long startTimeBucket, long endTimeBucket, int limit,
        int from) throws ParseException {
        SearchRequestBuilder searchRequestBuilder = getClient().prepareSearch(ApplicationAlarmTable.TABLE);
        searchRequestBuilder.setTypes(ApplicationAlarmTable.TABLE_TYPE);
        searchRequestBuilder.setSearchType(SearchType.DFS_QUERY_THEN_FETCH);

        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        boolQueryBuilder.must().add(QueryBuilders.rangeQuery(ApplicationAlarmTable.LAST_TIME_BUCKET.getName()).gte(startTimeBucket).lte(endTimeBucket));
        if (StringUtils.isNotEmpty(keyword)) {
            boolQueryBuilder.must().add(QueryBuilders.matchQuery(ApplicationAlarmTable.ALARM_CONTENT.getName(), keyword));
        }

        searchRequestBuilder.setQuery(boolQueryBuilder);
        searchRequestBuilder.setSize(limit);
        searchRequestBuilder.setFrom(from);

        SearchResponse searchResponse = searchRequestBuilder.execute().actionGet();
        SearchHit[] searchHits = searchResponse.getHits().getHits();

        Alarm alarm = new Alarm();
        alarm.setTotal((int)searchResponse.getHits().getTotalHits());
        for (SearchHit searchHit : searchHits) {
            AlarmItem alarmItem = new AlarmItem();
            alarmItem.setId(((Number)searchHit.getSource().get(ApplicationAlarmTable.APPLICATION_ID.getName())).intValue());
            alarmItem.setContent((String)searchHit.getSource().get(ApplicationAlarmTable.ALARM_CONTENT.getName()));

            long lastTimeBucket = ((Number)searchHit.getSource().get(ApplicationAlarmTable.LAST_TIME_BUCKET.getName())).longValue();
            alarmItem.setStartTime(TimeBucketUtils.INSTANCE.formatMinuteTimeBucket(lastTimeBucket));
            alarmItem.setAlarmType(AlarmType.APPLICATION);

            int alarmType = ((Number)searchHit.getSource().get(ApplicationAlarmTable.ALARM_TYPE.getName())).intValue();
            if (org.apache.skywalking.apm.collector.storage.table.alarm.AlarmType.SLOW_RTT.getValue() == alarmType) {
                alarmItem.setCauseType(CauseType.SLOW_RESPONSE);
            } else if (org.apache.skywalking.apm.collector.storage.table.alarm.AlarmType.ERROR_RATE.getValue() == alarmType) {
                alarmItem.setCauseType(CauseType.LOW_SUCCESS_RATE);
            }

            alarm.getItems().add(alarmItem);
        }
        return alarm;
    }
}
