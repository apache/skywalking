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

import com.google.common.base.Strings;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.alarm.AlarmRecord;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.Tag;
import org.apache.skywalking.oap.server.core.config.ConfigService;
import org.apache.skywalking.oap.server.core.query.enumeration.Scope;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.type.AlarmMessage;
import org.apache.skywalking.oap.server.core.query.type.Alarms;
import org.apache.skywalking.oap.server.core.storage.query.IAlarmQueryDAO;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCClient;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.SQLAndParameters;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.TableHelper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static java.util.Comparator.comparing;
import static java.util.Objects.nonNull;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.H2TableInstaller.ID_COLUMN;

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
                    final var message = new AlarmMessage();
                    message.setId(resultSet.getString(AlarmRecord.ID0));
                    message.setId1(resultSet.getString(AlarmRecord.ID1));
                    message.setMessage(resultSet.getString(AlarmRecord.ALARM_MESSAGE));
                    message.setStartTime(resultSet.getLong(AlarmRecord.START_TIME));
                    message.setScope(Scope.Finder.valueOf(resultSet.getInt(AlarmRecord.SCOPE)));
                    message.setScopeId(resultSet.getInt(AlarmRecord.SCOPE));
                    String dataBinaryBase64 = resultSet.getString(AlarmRecord.TAGS_RAW_DATA);
                    if (!Strings.isNullOrEmpty(dataBinaryBase64)) {
                        parserDataBinaryBase64(dataBinaryBase64, message.getTags());
                    }
                    alarmMsgs.add(message);
                }
                return null;
            }, sqlAndParameters.parameters());
        }
        return new Alarms(
            alarmMsgs
                .stream()
                .sorted(comparing(AlarmMessage::getStartTime).reversed())
                .skip(from)
                .limit(limit)
                .collect(toList())
        );
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
        /**
         * This is an AdditionalEntity feature, see:
         * {@link org.apache.skywalking.oap.server.core.storage.annotation.SQLDatabase.AdditionalEntity}
         */
        if (!CollectionUtils.isEmpty(tags)) {
            for (int i = 0; i < tags.size(); i++) {
                sql.append(" inner join ").append(AlarmRecord.ADDITIONAL_TAG_TABLE).append(" ");
                sql.append(AlarmRecord.ADDITIONAL_TAG_TABLE + i);
                sql.append(" on ").append(AlarmRecord.INDEX_NAME).append(".").append(ID_COLUMN).append(" = ");
                sql.append(AlarmRecord.ADDITIONAL_TAG_TABLE + i).append(".").append(ID_COLUMN);
            }
        }
        sql.append(" where 1 = 1");
        if (Objects.nonNull(scopeId)) {
            sql.append(" and ").append(AlarmRecord.SCOPE).append(" = ?");
            parameters.add(scopeId);
        }
        if (startTB != 0 && endTB != 0) {
            sql.append(" and ").append(AlarmRecord.INDEX_NAME).append(".").append(AlarmRecord.TIME_BUCKET).append(" >= ?");
            parameters.add(startTB);
            sql.append(" and ").append(AlarmRecord.INDEX_NAME).append(".").append(AlarmRecord.TIME_BUCKET).append(" <= ?");
            parameters.add(endTB);
        }

        if (!Strings.isNullOrEmpty(keyword)) {
            sql.append(" and ").append(AlarmRecord.ALARM_MESSAGE).append(" like concat('%',?,'%') ");
            parameters.add(keyword);
        }
        if (CollectionUtils.isNotEmpty(tags)) {
            for (int i = 0; i < tags.size(); i++) {
                sql.append(" and ").append(AlarmRecord.ADDITIONAL_TAG_TABLE + i).append(".");
                sql.append(AlarmRecord.TAGS).append(" = ?");
                parameters.add(tags.get(i).toString());
            }
        }
        sql.append(" order by ").append(AlarmRecord.START_TIME).append(" desc ");
        sql.append(" limit ").append(from + limit);

        return new SQLAndParameters(sql.toString(), parameters);
    }
}
