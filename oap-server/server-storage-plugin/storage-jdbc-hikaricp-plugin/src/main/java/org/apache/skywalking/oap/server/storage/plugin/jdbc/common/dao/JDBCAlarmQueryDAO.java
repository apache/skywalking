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
import lombok.SneakyThrows;
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static java.util.Objects.nonNull;
import static org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.H2TableInstaller.ID_COLUMN;

public class JDBCAlarmQueryDAO implements IAlarmQueryDAO {
    protected final JDBCClient jdbcClient;

    private final ModuleManager manager;

    private List<String> searchableTagKeys;

    public JDBCAlarmQueryDAO(final JDBCClient jdbcClient,
                             final ModuleManager manager) {
        this.jdbcClient = jdbcClient;
        this.manager = manager;
    }

    @Override
    @SneakyThrows
    public Alarms getAlarm(Integer scopeId, String keyword, int limit, int from,
                           Duration duration, final List<Tag> tags) {
        long startTB = 0;
        long endTB = 0;
        if (nonNull(duration)) {
            startTB = duration.getStartTimeBucketInSec();
            endTB = duration.getEndTimeBucketInSec();
        }
        if (searchableTagKeys == null) {
            final ConfigService configService = manager.find(CoreModule.NAME)
                    .provider()
                    .getService(ConfigService.class);
            searchableTagKeys = Arrays.asList(configService.getSearchableAlarmTags().split(Const.COMMA));
        }
        StringBuilder sql = new StringBuilder();
        List<Object> parameters = new ArrayList<>(10);
        sql.append("from ").append(AlarmRecord.INDEX_NAME);
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
        sql.append(" where ");
        sql.append(" 1=1 ");
        if (Objects.nonNull(scopeId)) {
            sql.append(" and ").append(AlarmRecord.SCOPE).append(" = ?");
            parameters.add(scopeId.intValue());
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
                final int foundIdx = searchableTagKeys.indexOf(tags.get(i).getKey());
                if (foundIdx > -1) {
                    sql.append(" and ").append(AlarmRecord.ADDITIONAL_TAG_TABLE + i).append(".");
                    sql.append(AlarmRecord.TAGS).append(" = ?");
                    parameters.add(tags.get(i).toString());
                } else {
                    //If the tag is not searchable, but is required, then don't need to run the real query.
                    return new Alarms();
                }
            }
        }
        sql.append(" order by ").append(AlarmRecord.START_TIME).append(" desc ");

        buildLimit(sql, from, limit);

        return jdbcClient.executeQuery("select * " + sql, resultSet -> {
            final var alarms = new Alarms();

            while (resultSet.next()) {
                final var message = new AlarmMessage();
                message.setId(resultSet.getString(AlarmRecord.ID0));
                message.setId1(resultSet.getString(AlarmRecord.ID1));
                message.setMessage(resultSet.getString(AlarmRecord.ALARM_MESSAGE));
                message.setStartTime(resultSet.getLong(AlarmRecord.START_TIME));
                message.setScope(Scope.Finder.valueOf(resultSet.getInt(AlarmRecord.SCOPE)));
                message.setScopeId(resultSet.getInt(AlarmRecord.SCOPE));
                String dataBinaryBase64 = resultSet.getString(AlarmRecord.TAGS_RAW_DATA);
                if (!com.google.common.base.Strings.isNullOrEmpty(dataBinaryBase64)) {
                    parserDataBinaryBase64(dataBinaryBase64, message.getTags());
                }
                alarms.getMsgs().add(message);
            }

            return alarms;
        }, parameters.toArray(new Object[0]));
    }

    protected void buildLimit(StringBuilder sql, int from, int limit) {
        sql.append(" LIMIT ").append(limit);
        sql.append(" OFFSET ").append(from);
    }
}
