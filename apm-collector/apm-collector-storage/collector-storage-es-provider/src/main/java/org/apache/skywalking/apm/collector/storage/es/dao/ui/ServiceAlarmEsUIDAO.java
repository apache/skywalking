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
import org.apache.skywalking.apm.collector.storage.dao.ui.IServiceAlarmUIDAO;
import org.apache.skywalking.apm.collector.storage.es.base.dao.EsDAO;
import org.apache.skywalking.apm.collector.storage.table.alarm.ServiceAlarmTable;
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
public class ServiceAlarmEsUIDAO extends EsDAO implements IServiceAlarmUIDAO {

    public ServiceAlarmEsUIDAO(ElasticSearchClient client) {
        super(client);
    }

    @Override
    public Alarm loadAlarmList(String keyword, long startTimeBucket, long endTimeBucket, int limit,
        int from) throws ParseException {
        SearchRequestBuilder searchRequestBuilder = getClient().prepareSearch(ServiceAlarmTable.TABLE);
        searchRequestBuilder.setTypes(ServiceAlarmTable.TABLE_TYPE);
        searchRequestBuilder.setSearchType(SearchType.DFS_QUERY_THEN_FETCH);

        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        boolQueryBuilder.must().add(QueryBuilders.rangeQuery(ServiceAlarmTable.COLUMN_LAST_TIME_BUCKET).gte(startTimeBucket).lte(endTimeBucket));
        if (StringUtils.isNotEmpty(keyword)) {
            boolQueryBuilder.must().add(QueryBuilders.matchQuery(ServiceAlarmTable.COLUMN_ALARM_CONTENT, keyword));
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
            alarmItem.setId(((Number)searchHit.getSource().get(ServiceAlarmTable.COLUMN_SERVICE_ID)).intValue());
            alarmItem.setTitle((String)searchHit.getSource().get(ServiceAlarmTable.COLUMN_ALARM_CONTENT));
            alarmItem.setContent((String)searchHit.getSource().get(ServiceAlarmTable.COLUMN_ALARM_CONTENT));

            long lastTimeBucket = ((Number)searchHit.getSource().get(ServiceAlarmTable.COLUMN_LAST_TIME_BUCKET)).longValue();
            alarmItem.setStartTime(TimeBucketUtils.INSTANCE.formatMinuteTimeBucket(lastTimeBucket));
            alarmItem.setAlarmType(AlarmType.SERVICE);

            int alarmType = ((Number)searchHit.getSource().get(ServiceAlarmTable.COLUMN_ALARM_TYPE)).intValue();
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
