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

package org.apache.skywalking.oap.server.storage.plugin.elasticsearch.query;

import com.google.common.base.Strings;
import org.apache.skywalking.library.elasticsearch.requests.search.BoolQueryBuilder;
import org.apache.skywalking.library.elasticsearch.requests.search.Query;
import org.apache.skywalking.library.elasticsearch.requests.search.Search;
import org.apache.skywalking.library.elasticsearch.requests.search.SearchBuilder;
import org.apache.skywalking.library.elasticsearch.requests.search.Sort;
import org.apache.skywalking.library.elasticsearch.response.search.SearchHit;
import org.apache.skywalking.library.elasticsearch.response.search.SearchResponse;
import org.apache.skywalking.oap.server.core.alarm.AlarmRecord;
import org.apache.skywalking.oap.server.core.alarm.AlarmRecoveryRecord;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.Tag;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.type.AlarmMessage;
import org.apache.skywalking.oap.server.core.query.type.Alarms;
import org.apache.skywalking.oap.server.core.storage.query.IAlarmQueryDAO;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.ElasticSearchConverter;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.EsDAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.IndexController;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.MatchCNameBuilder;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class AlarmQueryEsDAO extends EsDAO implements IAlarmQueryDAO {

    public AlarmQueryEsDAO(ElasticSearchClient client) {
        super(client);
    }

    @Override
    public Alarms getAlarm(final Integer scopeId, final String keyword, final int limit,
                           final int from,
                           final Duration duration,
                           final List<Tag> tags)
            throws IOException {
        long startTB = duration.getStartTimeBucketInSec();
        long endTB = duration.getEndTimeBucketInSec();
        final String index =
                IndexController.LogicIndicesRegister.getPhysicalTableName(AlarmRecord.INDEX_NAME);
        final BoolQueryBuilder query = Query.bool();
        if (IndexController.LogicIndicesRegister.isMergedTable(AlarmRecord.INDEX_NAME)) {
            query.must(Query.term(IndexController.LogicIndicesRegister.RECORD_TABLE_NAME, AlarmRecord.INDEX_NAME));
        }

        if (startTB != 0 && endTB != 0) {
            query.must(Query.range(AlarmRecord.TIME_BUCKET).gte(startTB).lte(endTB));
        }

        if (Objects.nonNull(scopeId)) {
            query.must(Query.term(AlarmRecord.SCOPE, scopeId));
        }

        if (!Strings.isNullOrEmpty(keyword)) {
            String matchCName = MatchCNameBuilder.INSTANCE.build(AlarmRecord.ALARM_MESSAGE);
            query.must(Query.matchPhrase(matchCName, keyword));
        }

        if (CollectionUtils.isNotEmpty(tags)) {
            tags.forEach(tag -> query.must(Query.term(AlarmRecord.TAGS, tag.toString())));
        }

        final SearchBuilder search =
                Search.builder().query(query)
                        .size(limit).from(from)
                        .sort(AlarmRecord.START_TIME, Sort.Order.DESC);

        SearchResponse response = getClient().search(index, search.build());

        Alarms alarms = new Alarms();

        for (SearchHit searchHit : response.getHits().getHits()) {
            AlarmRecord.Builder builder = new AlarmRecord.Builder();
            AlarmRecord alarmRecord = builder.storage2Entity(new ElasticSearchConverter.ToEntity(AlarmRecord.INDEX_NAME, searchHit.getSource()));
            AlarmMessage alarmMessage = buildAlarmMessage(alarmRecord);
            if (!CollectionUtils.isEmpty(alarmRecord.getTagsRawData())) {
                parseDataBinary(alarmRecord.getTagsRawData(), alarmMessage.getTags());
            }
            alarms.getMsgs().add(alarmMessage);
        }
        updateAlarmRecoveryTime(alarms, duration);
        return alarms;
    }

    private void updateAlarmRecoveryTime(Alarms alarms, Duration duration) throws IOException {
        List<AlarmMessage> alarmMessages = alarms.getMsgs();
        Map<String, AlarmRecoveryRecord> alarmRecoveryRecordMap = getAlarmRecoveryRecord(alarmMessages, duration);
        alarmMessages.forEach(alarmMessage -> {
            AlarmRecoveryRecord alarmRecoveryRecord = alarmRecoveryRecordMap.get(alarmMessage.getUuid());
            if (alarmRecoveryRecord != null) {
                alarmMessage.setRecoveryTime(alarmRecoveryRecord.getRecoveryTime());
            }
        });

    }

    private Map<String, AlarmRecoveryRecord> getAlarmRecoveryRecord(List<AlarmMessage> msgs, Duration duration) throws IOException {
        Map<String, AlarmRecoveryRecord> result = new HashMap<>();
        if (CollectionUtils.isEmpty(msgs)) {
            return result;
        }
        List<String> uuids = msgs.stream().map(AlarmMessage::getUuid).collect(Collectors.toList());
        long startTB = duration.getStartTimeBucketInSec();
        long endTB = duration.getEndTimeBucketInSec();
        final String index =
                IndexController.LogicIndicesRegister.getPhysicalTableName(AlarmRecoveryRecord.INDEX_NAME);
        final BoolQueryBuilder query = Query.bool();
        if (IndexController.LogicIndicesRegister.isMergedTable(AlarmRecoveryRecord.INDEX_NAME)) {
            query.must(Query.term(IndexController.LogicIndicesRegister.RECORD_TABLE_NAME, AlarmRecoveryRecord.INDEX_NAME));
        }
        if (startTB != 0 && endTB != 0) {
            query.must(Query.range(AlarmRecord.TIME_BUCKET).gte(startTB).lte(endTB));
        }
        query.must(Query.terms(AlarmRecoveryRecord.UUID, uuids));
        final SearchBuilder search =
                Search.builder().query(query);
        SearchResponse response = getClient().search(index, search.build());
        for (SearchHit searchHit : response.getHits().getHits()) {
            AlarmRecoveryRecord.Builder builder = new AlarmRecoveryRecord.Builder();
            AlarmRecoveryRecord alarmRecoveryRecord = builder.storage2Entity(new ElasticSearchConverter.ToEntity(AlarmRecoveryRecord.INDEX_NAME, searchHit.getSource()));
            result.put(alarmRecoveryRecord.getUuid(), alarmRecoveryRecord);
        }
        return result;
    }
}
