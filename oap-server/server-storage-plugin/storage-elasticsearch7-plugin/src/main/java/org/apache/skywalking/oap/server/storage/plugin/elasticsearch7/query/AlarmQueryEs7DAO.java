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

package org.apache.skywalking.oap.server.storage.plugin.elasticsearch7.query;

import com.google.common.base.Strings;
import org.apache.skywalking.oap.server.core.alarm.AlarmRecord;
import org.apache.skywalking.oap.server.core.query.type.AlarmMessage;
import org.apache.skywalking.oap.server.core.query.type.Alarms;
import org.apache.skywalking.oap.server.core.query.enumeration.Scope;
import org.apache.skywalking.oap.server.core.storage.query.IAlarmQueryDAO;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.EsDAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.MatchCNameBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;

import java.io.IOException;
import java.util.Objects;

public class AlarmQueryEs7DAO extends EsDAO implements IAlarmQueryDAO {

    public AlarmQueryEs7DAO(ElasticSearchClient client) {
        super(client);
    }

    @Override
    public Alarms getAlarm(final Integer scopeId, final String keyword, final int limit, final int from,
        final long startTB, final long endTB) throws IOException {
        SearchSourceBuilder sourceBuilder = SearchSourceBuilder.searchSource();

        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        boolQueryBuilder.must().add(QueryBuilders.rangeQuery(AlarmRecord.TIME_BUCKET).gte(startTB).lte(endTB));

        if (Objects.nonNull(scopeId)) {
            boolQueryBuilder.must().add(QueryBuilders.termQuery(AlarmRecord.SCOPE, scopeId.intValue()));
        }

        if (!Strings.isNullOrEmpty(keyword)) {
            String matchCName = MatchCNameBuilder.INSTANCE.build(AlarmRecord.ALARM_MESSAGE);
            boolQueryBuilder.must().add(QueryBuilders.matchPhraseQuery(matchCName, keyword));
        }

        sourceBuilder.query(boolQueryBuilder).sort(AlarmRecord.START_TIME, SortOrder.DESC);
        sourceBuilder.size(limit);
        sourceBuilder.from(from);

        SearchResponse response = getClient().search(AlarmRecord.INDEX_NAME, sourceBuilder);

        Alarms alarms = new Alarms();
        alarms.setTotal((int) response.getHits().getTotalHits().value);

        for (SearchHit searchHit : response.getHits().getHits()) {
            AlarmRecord.Builder builder = new AlarmRecord.Builder();
            AlarmRecord alarmRecord = builder.storage2Entity(searchHit.getSourceAsMap());

            AlarmMessage message = new AlarmMessage();
            message.setId(String.valueOf(alarmRecord.getId0()));
            message.setMessage(alarmRecord.getAlarmMessage());
            message.setStartTime(alarmRecord.getStartTime());
            message.setScope(Scope.Finder.valueOf(alarmRecord.getScope()));
            message.setScopeId(alarmRecord.getScope());
            alarms.getMsgs().add(message);
        }
        return alarms;
    }
}
