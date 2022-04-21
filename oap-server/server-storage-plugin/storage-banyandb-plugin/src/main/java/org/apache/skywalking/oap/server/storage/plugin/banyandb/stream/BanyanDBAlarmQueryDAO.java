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
import org.apache.skywalking.banyandb.v1.client.RowEntity;
import org.apache.skywalking.banyandb.v1.client.StreamQuery;
import org.apache.skywalking.banyandb.v1.client.StreamQueryResponse;
import org.apache.skywalking.banyandb.v1.client.TimestampRange;
import org.apache.skywalking.oap.server.core.alarm.AlarmRecord;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.Tag;
import org.apache.skywalking.oap.server.core.query.enumeration.Scope;
import org.apache.skywalking.oap.server.core.query.type.AlarmMessage;
import org.apache.skywalking.oap.server.core.query.type.Alarms;
import org.apache.skywalking.oap.server.core.storage.query.IAlarmQueryDAO;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBConverter;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBStorageClient;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.MetadataRegistry;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

/**
 * {@link org.apache.skywalking.oap.server.core.alarm.AlarmRecord} is a stream,
 * which can be used to build a {@link org.apache.skywalking.oap.server.core.query.type.AlarmMessage}
 */
public class BanyanDBAlarmQueryDAO extends AbstractBanyanDBDAO implements IAlarmQueryDAO {
    private final MetadataRegistry.PartialMetadata alarmRecordMetadata =
            MetadataRegistry.INSTANCE.findSchema(AlarmRecord.INDEX_NAME);

    public BanyanDBAlarmQueryDAO(BanyanDBStorageClient client) {
        super(client);
    }

    @Override
    public Alarms getAlarm(Integer scopeId, String keyword, int limit, int from, long startTB, long endTB, List<Tag> tags) throws IOException {
        TimestampRange tsRange = null;
        if (startTB > 0 && endTB > 0) {
            tsRange = new TimestampRange(TimeBucket.getTimestamp(startTB), TimeBucket.getTimestamp(endTB));
        }

        StreamQueryResponse resp = query(alarmRecordMetadata,
                ImmutableSet.of(AlarmRecord.SCOPE, AlarmRecord.START_TIME, AlarmRecord.ID0, AlarmRecord.ID1, AlarmRecord.ALARM_MESSAGE, AlarmRecord.TAGS_RAW_DATA),
                tsRange,
                new QueryBuilder() {
                    @Override
                    public void apply(StreamQuery query) {
                        if (Objects.nonNull(scopeId)) {
                            query.appendCondition(eq(AlarmRecord.SCOPE, (long) scopeId));
                        }

                        // TODO: support keyword search

                        if (CollectionUtils.isNotEmpty(tags)) {
                            for (final Tag tag : tags) {
                                // TODO: check whether tags in the alarm are indexed
                                query.appendCondition(eq(tag.getKey(), tag.getValue()));
                            }
                        }
                        query.setLimit(limit);
                        query.setOffset(from);
                    }
                });

        Alarms alarms = new Alarms();
        alarms.setTotal(resp.size());

        for (final RowEntity rowEntity : resp.getElements()) {
            AlarmRecord.Builder builder = new AlarmRecord.Builder();
            AlarmRecord alarmRecord = builder.storage2Entity(
                    new BanyanDBConverter.StreamToEntity(rowEntity)
            );

            AlarmMessage message = new AlarmMessage();
            message.setId(String.valueOf(alarmRecord.getId0()));
            message.setId1(String.valueOf(alarmRecord.getId1()));
            message.setMessage(alarmRecord.getAlarmMessage());
            message.setStartTime(alarmRecord.getStartTime());
            message.setScope(Scope.Finder.valueOf(alarmRecord.getScope()));
            message.setScopeId(alarmRecord.getScope());
            if (!CollectionUtils.isEmpty(alarmRecord.getTagsRawData())) {
                parserDataBinary(alarmRecord.getTagsRawData(), message.getTags());
            }
            alarms.getMsgs().add(message);
        }
        return alarms;
    }
}
