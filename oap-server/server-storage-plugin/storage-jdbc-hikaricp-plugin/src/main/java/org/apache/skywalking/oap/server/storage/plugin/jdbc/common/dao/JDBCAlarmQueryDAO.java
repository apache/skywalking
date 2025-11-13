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

package org.apache.skywalking.oap.server.storage.plugin.jdbc.common.dao;

import com.google.common.base.Charsets;
import com.google.common.base.Strings;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.alarm.AlarmRecord;
import org.apache.skywalking.oap.server.core.alarm.AlarmRecoveryRecord;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.Tag;
import org.apache.skywalking.oap.server.core.config.ConfigService;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.type.AlarmMessage;
import org.apache.skywalking.oap.server.core.query.type.Alarms;
import org.apache.skywalking.oap.server.core.storage.query.IAlarmQueryDAO;
import org.apache.skywalking.oap.server.core.storage.type.Convert2Entity;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCClient;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.JDBCEntityConverters;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.JDBCTableInstaller;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.SQLAndParameters;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.TableHelper;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static java.util.Comparator.comparing;
import static java.util.Objects.nonNull;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

@Slf4j
@RequiredArgsConstructor
public class JDBCAlarmQueryDAO implements IAlarmQueryDAO {
    protected final JDBCClient jdbcClient;
    protected final ModuleManager manager;
    protected final TableHelper tableHelper;

    private Set<String> searchableTagKeys;

    @Override
    @SneakyThrows
    public Alarms getAlarm(Integer scopeId, String keyword, int limit, int from,
                           Duration duration, final List<Tag> tags) {
        if (searchableTagKeys == null) {
            final ConfigService configService = manager.find(CoreModule.NAME)
                    .provider()
                    .getService(ConfigService.class);
            searchableTagKeys = new HashSet<>(Arrays.asList(configService.getSearchableAlarmTags().split(Const.COMMA)));
        }
        // If the tag is not searchable, but is required, then we don't need to run the real query.
        if (tags != null && !searchableTagKeys.containsAll(tags.stream().map(Tag::getKey).collect(toSet()))) {
            log.warn(
                    "Searching tags that are not searchable: {}",
                    tags.stream().map(Tag::getKey).filter(not(searchableTagKeys::contains)).collect(toSet()));
            return new Alarms();
        }

        final var tables = tableHelper.getTablesForRead(
                AlarmRecord.INDEX_NAME, duration.getStartTimeBucket(), duration.getEndTimeBucket()
        );
        final var alarmMsgs = new ArrayList<AlarmMessage>();

        for (final var table : tables) {
            final var sqlAndParameters = buildSQL(scopeId, keyword, limit, from, duration, tags, table);
            jdbcClient.executeQuery(sqlAndParameters.sql(), resultSet -> {
                while (resultSet.next()) {
                    AlarmRecord.Builder builder = new AlarmRecord.Builder();
                    Convert2Entity convert2Entity = JDBCEntityConverters.toEntity(resultSet);
                    AlarmRecord alarmRecord = builder.storage2Entity(convert2Entity);
                    AlarmMessage alarmMessage = buildAlarmMessage(alarmRecord);
                    if (!CollectionUtils.isEmpty(alarmRecord.getTagsRawData())) {
                        parseDataBinaryBase64(
                                new String(alarmRecord.getTagsRawData(), Charsets.UTF_8), alarmMessage.getTags());
                    }
                    alarmMsgs.add(alarmMessage);
                }
                return null;
            }, sqlAndParameters.parameters());
        }
        Alarms alarms = new Alarms(
                alarmMsgs
                        .stream()
                        .sorted(comparing(AlarmMessage::getStartTime).reversed())
                        .skip(from)
                        .limit(limit)
                        .collect(toList())
        );
        updateAlarmRecoveryTime(alarms, duration);
        return alarms;
    }

    private void updateAlarmRecoveryTime(Alarms alarms, Duration duration) throws SQLException {
        List<AlarmMessage> alarmMessages = alarms.getMsgs();
        Map<String, AlarmRecoveryRecord> alarmRecoveryRecordMap = getAlarmRecoveryRecord(alarmMessages, duration);
        alarmMessages.forEach(alarmMessage -> {
            AlarmRecoveryRecord alarmRecoveryRecord = alarmRecoveryRecordMap.get(alarmMessage.getUuid());
            if (alarmRecoveryRecord != null) {
                alarmMessage.setRecoveryTime(alarmRecoveryRecord.getRecoveryTime());
            }
        });

    }

    private Map<String, AlarmRecoveryRecord> getAlarmRecoveryRecord(List<AlarmMessage> msgs, Duration duration) throws SQLException {
        Map<String, AlarmRecoveryRecord> result = new HashMap<>();
        if (CollectionUtils.isEmpty(msgs)) {
            return result;
        }
        List<String> uuids = msgs.stream().map(AlarmMessage::getUuid).collect(toList());
        final var tables = tableHelper.getTablesForRead(
                AlarmRecoveryRecord.INDEX_NAME, duration.getStartTimeBucket(), duration.getEndTimeBucket()
        );
        for (final var table : tables) {
            final var sqlAndParameters = buildSQL4Recovery(uuids, table);
            jdbcClient.executeQuery(sqlAndParameters.sql(), resultSet -> {
                while (resultSet.next()) {
                    AlarmRecoveryRecord.Builder builder = new AlarmRecoveryRecord.Builder();
                    Convert2Entity convert2Entity = JDBCEntityConverters.toEntity(resultSet);
                    AlarmRecoveryRecord alarmRecoveryRecord = builder.storage2Entity(convert2Entity);
                    result.put(alarmRecoveryRecord.getUuid(), alarmRecoveryRecord);
                }
                return null;
            }, sqlAndParameters.parameters());
        }
        return result;
    }

    protected SQLAndParameters buildSQL(Integer scopeId, String keyword, int limit, int from,
                                        Duration duration, List<Tag> tags, String table) {
        final var sql = new StringBuilder();
        final var parameters = new ArrayList<>();

        long startTB = 0;
        long endTB = 0;
        if (nonNull(duration)) {
            startTB = duration.getStartTimeBucketInSec();
            endTB = duration.getEndTimeBucketInSec();
        }
        sql.append("select * from ").append(table);
        /*
         * This is an AdditionalEntity feature, see:
         * {@link org.apache.skywalking.oap.server.core.storage.annotation.SQLDatabase.AdditionalEntity}
         */
        final var timeBucket = TableHelper.getTimeBucket(table);
        final var tagTable = TableHelper.getTable(AlarmRecord.ADDITIONAL_TAG_TABLE, timeBucket);

        if (!CollectionUtils.isEmpty(tags)) {
            for (int i = 0; i < tags.size(); i++) {
                sql.append(" inner join ").append(tagTable).append(" ");
                sql.append(tagTable + i);
                sql.append(" on ").append(table).append(".").append(JDBCTableInstaller.ID_COLUMN).append(" = ");
                sql.append(tagTable + i).append(".").append(JDBCTableInstaller.ID_COLUMN);
            }
        }
        sql.append(" where ")
                .append(table).append(".").append(JDBCTableInstaller.TABLE_COLUMN).append(" = ? ");
        parameters.add(AlarmRecord.INDEX_NAME);
        if (Objects.nonNull(scopeId)) {
            sql.append(" and ").append(AlarmRecord.SCOPE).append(" = ?");
            parameters.add(scopeId);
        }
        if (startTB != 0 && endTB != 0) {
            sql.append(" and ").append(table).append(".").append(AlarmRecord.TIME_BUCKET).append(" >= ?");
            parameters.add(startTB);
            sql.append(" and ").append(table).append(".").append(AlarmRecord.TIME_BUCKET).append(" <= ?");
            parameters.add(endTB);
        }

        if (!Strings.isNullOrEmpty(keyword)) {
            sql.append(" and ").append(AlarmRecord.ALARM_MESSAGE).append(" like concat('%',?,'%') ");
            parameters.add(keyword);
        }
        if (CollectionUtils.isNotEmpty(tags)) {
            for (int i = 0; i < tags.size(); i++) {
                sql.append(" and ").append(tagTable + i).append(".");
                sql.append(AlarmRecord.TAGS).append(" = ?");
                parameters.add(tags.get(i).toString());
            }
        }
        sql.append(" order by ").append(AlarmRecord.START_TIME).append(" desc ");
        sql.append(" limit ").append(from + limit);

        return new SQLAndParameters(sql.toString(), parameters);
    }

    private SQLAndParameters buildSQL4Recovery(List<String> uuids, String table) {
        final var sql = new StringBuilder();
        final var parameters = new ArrayList<>();
        sql.append("select * from ").append(table);
        sql.append(" where ")
                .append(table).append(".").append(JDBCTableInstaller.TABLE_COLUMN).append(" = ? ");
        parameters.add(AlarmRecoveryRecord.INDEX_NAME);
        sql.append(" and ").append(AlarmRecoveryRecord.UUID).append(" in ")
                .append(uuids.stream().map(it -> "?").collect(joining(", ", "(", ")")));
        parameters.addAll(uuids);
        return new SQLAndParameters(sql.toString(), parameters);
    }
}
