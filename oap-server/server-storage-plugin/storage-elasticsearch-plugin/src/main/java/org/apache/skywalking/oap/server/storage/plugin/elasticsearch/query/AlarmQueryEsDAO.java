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

import java.io.IOException;
import java.util.List;
import java.util.Objects;

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
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.ElasticSearchConverter;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.EsDAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.IndexController;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.MatchCNameBuilder;

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
            Long recoveryTime = getAlarmRecoveryTime(alarmRecord.getUuid(), duration);
            AlarmMessage alarmMessage = buildAlarmMessage(alarmRecord, recoveryTime);
            if (!CollectionUtils.isEmpty(alarmRecord.getTagsRawData())) {
                parseDataBinary(alarmRecord.getTagsRawData(), alarmMessage.getTags());
            }
            alarms.getMsgs().add(alarmMessage);
        }
        return alarms;
    }

    private Long getAlarmRecoveryTime(String uuid, Duration duration) {
        if (StringUtil.isBlank(uuid)) {
            return null;
        }
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
        query.must(Query.term(AlarmRecoveryRecord.UUID, uuid));
        final SearchBuilder search =
                Search.builder().query(query)
                        .size(1).from(1);
        SearchResponse response = getClient().search(index, search.build());
        for (SearchHit searchHit : response.getHits().getHits()) {
            AlarmRecoveryRecord.Builder builder = new AlarmRecoveryRecord.Builder();
            AlarmRecoveryRecord alarmRecoveryRecord = builder.storage2Entity(new ElasticSearchConverter.ToEntity(AlarmRecoveryRecord.INDEX_NAME, searchHit.getSource()));
            return alarmRecoveryRecord.getRecoveryTime();
        }
        return null;
    }
}
