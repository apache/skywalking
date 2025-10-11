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

package org.apache.skywalking.oap.server.storage.plugin.banyandb.stream;

import com.google.common.collect.ImmutableSet;
import org.apache.skywalking.banyandb.v1.client.AbstractQuery;
import org.apache.skywalking.banyandb.v1.client.RowEntity;
import org.apache.skywalking.banyandb.v1.client.StreamQuery;
import org.apache.skywalking.banyandb.v1.client.StreamQueryResponse;
import org.apache.skywalking.oap.server.core.alarm.AlarmRecord;
import org.apache.skywalking.oap.server.core.alarm.AlarmRecoveryRecord;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.Tag;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.type.AlarmMessage;
import org.apache.skywalking.oap.server.core.query.type.Alarms;
import org.apache.skywalking.oap.server.core.storage.query.IAlarmQueryDAO;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBConverter;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBStorageClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * {@link org.apache.skywalking.oap.server.core.alarm.AlarmRecord} is a stream,
 * which can be used to build a {@link org.apache.skywalking.oap.server.core.query.type.AlarmMessage}
 */
public class BanyanDBAlarmQueryDAO extends AbstractBanyanDBDAO implements IAlarmQueryDAO {
    private static final Set<String> TAGS = ImmutableSet.of(AlarmRecord.SCOPE,
            AlarmRecord.NAME, AlarmRecord.ID0, AlarmRecord.ID1, AlarmRecord.UUID, AlarmRecord.ALARM_MESSAGE,
            AlarmRecord.START_TIME, AlarmRecord.RULE_NAME, AlarmRecord.TAGS, AlarmRecord.TAGS_RAW_DATA, AlarmRecord.SNAPSHOT);
    private static final Set<String> RECOVERY_TAGS = ImmutableSet.of(AlarmRecoveryRecord.SCOPE,
            AlarmRecoveryRecord.NAME, AlarmRecord.ID0, AlarmRecoveryRecord.ID1, AlarmRecoveryRecord.UUID,
            AlarmRecoveryRecord.ALARM_MESSAGE, AlarmRecoveryRecord.START_TIME, AlarmRecoveryRecord.RECOVERY_TIME,
            AlarmRecoveryRecord.RULE_NAME, AlarmRecoveryRecord.TAGS, AlarmRecoveryRecord.TAGS_RAW_DATA, AlarmRecoveryRecord.SNAPSHOT);

    public BanyanDBAlarmQueryDAO(BanyanDBStorageClient client) {
        super(client);
    }

    @Override
    public Alarms getAlarm(Integer scopeId, String keyword, int limit, int from, Duration duration, List<Tag> tags) throws IOException {
        final boolean isColdStage = duration != null && duration.isColdStage();
        StreamQueryResponse resp = query(isColdStage, AlarmRecord.INDEX_NAME, TAGS,
                getTimestampRange(duration),
                new QueryBuilder<StreamQuery>() {
                    @Override
                    public void apply(StreamQuery query) {
                        if (Objects.nonNull(scopeId)) {
                            query.and(eq(AlarmRecord.SCOPE, (long) scopeId));
                        }
                        if (StringUtil.isNotEmpty(keyword)) {
                            query.and(match(AlarmRecord.ALARM_MESSAGE, keyword));
                        }
                        if (CollectionUtils.isNotEmpty(tags)) {
                            List<String> tagsConditions = new ArrayList<>(tags.size());
                            for (final Tag tag : tags) {
                                tagsConditions.add(tag.toString());
                            }
                            query.and(having(AlarmRecord.TAGS, tagsConditions));
                        }

                        query.setLimit(limit);
                        query.setOffset(from);
                        query.setOrderBy(new StreamQuery.OrderBy(AbstractQuery.Sort.DESC));
                    }
                });

        Alarms alarms = new Alarms();

        for (final RowEntity rowEntity : resp.getElements()) {
            AlarmRecord.Builder builder = new AlarmRecord.Builder();
            AlarmRecord alarmRecord = builder.storage2Entity(
                    new BanyanDBConverter.StorageToStream(AlarmRecord.INDEX_NAME, rowEntity)
            );
            Long recoveryTime = getAlarmRecoveryTime(alarmRecord.getUuid(), duration);
            AlarmMessage alarmMessage = buildAlarmMessage(alarmRecord, recoveryTime);
            if (!CollectionUtils.isEmpty(alarmRecord.getTagsRawData())) {
                parseDataBinary(alarmRecord.getTagsRawData(), alarmMessage.getTags());
            }
            alarms.getMsgs().add(alarmMessage);
        }
        return alarms;
    }

    private Long getAlarmRecoveryTime(String uuid, Duration duration) throws IOException {
        if (StringUtil.isBlank(uuid)) {
            return null;
        }
        final boolean isColdStage = duration != null && duration.isColdStage();
        StreamQueryResponse resp = query(isColdStage, AlarmRecoveryRecord.INDEX_NAME, RECOVERY_TAGS,
                getTimestampRange(duration), new QueryBuilder<StreamQuery>() {
                    @Override
                    public void apply(StreamQuery query) {
                        query.and(eq(AlarmRecoveryRecord.UUID, uuid));
                    }
                });
        for (final RowEntity rowEntity : resp.getElements()) {
            AlarmRecoveryRecord.Builder builder = new AlarmRecoveryRecord.Builder();
            AlarmRecoveryRecord alarmRecoveryRecord = builder.storage2Entity(
                    new BanyanDBConverter.StorageToStream(AlarmRecoveryRecord.INDEX_NAME, rowEntity)
            );
            return alarmRecoveryRecord.getRecoveryTime();
        }
        return null;
    }
}
