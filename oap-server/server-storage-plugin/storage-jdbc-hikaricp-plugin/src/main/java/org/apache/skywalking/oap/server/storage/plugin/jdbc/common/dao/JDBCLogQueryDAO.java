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
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.manual.log.AbstractLogRecord;
import org.apache.skywalking.oap.server.core.analysis.manual.log.LogRecord;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.Tag;
import org.apache.skywalking.oap.server.core.config.ConfigService;
import org.apache.skywalking.oap.server.core.query.enumeration.Order;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.input.TraceScopeCondition;
import org.apache.skywalking.oap.server.core.query.type.ContentType;
import org.apache.skywalking.oap.server.core.query.type.Log;
import org.apache.skywalking.oap.server.core.query.type.Logs;
import org.apache.skywalking.oap.server.core.storage.query.ILogQueryDAO;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCClient;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.JDBCTableInstaller;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.SQLAndParameters;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.TableHelper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static java.util.Comparator.comparing;
import static java.util.Objects.nonNull;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.apache.skywalking.oap.server.core.analysis.manual.log.AbstractLogRecord.CONTENT;
import static org.apache.skywalking.oap.server.core.analysis.manual.log.AbstractLogRecord.CONTENT_TYPE;
import static org.apache.skywalking.oap.server.core.analysis.manual.log.AbstractLogRecord.ENDPOINT_ID;
import static org.apache.skywalking.oap.server.core.analysis.manual.log.AbstractLogRecord.SERVICE_ID;
import static org.apache.skywalking.oap.server.core.analysis.manual.log.AbstractLogRecord.SERVICE_INSTANCE_ID;
import static org.apache.skywalking.oap.server.core.analysis.manual.log.AbstractLogRecord.SPAN_ID;
import static org.apache.skywalking.oap.server.core.analysis.manual.log.AbstractLogRecord.TAGS_RAW_DATA;
import static org.apache.skywalking.oap.server.core.analysis.manual.log.AbstractLogRecord.TIMESTAMP;
import static org.apache.skywalking.oap.server.core.analysis.manual.log.AbstractLogRecord.TRACE_ID;
import static org.apache.skywalking.oap.server.core.analysis.manual.log.AbstractLogRecord.TRACE_SEGMENT_ID;

@Slf4j
@RequiredArgsConstructor
public class JDBCLogQueryDAO implements ILogQueryDAO {
    private final JDBCClient jdbcClient;
    private final ModuleManager manager;
    private final TableHelper tableHelper;
    private Set<String> searchableTagKeys;

    @Override
    @SneakyThrows
    public Logs queryLogs(String serviceId,
                          String serviceInstanceId,
                          String endpointId,
                          TraceScopeCondition relatedTrace,
                          Order queryOrder,
                          int from,
                          int limit,
                          final Duration duration,
                          final List<Tag> tags,
                          final List<String> keywordsOfContent,
                          final List<String> excludingKeywordsOfContent) {
        if (searchableTagKeys == null) {
            final ConfigService configService = manager.find(CoreModule.NAME)
                                                       .provider()
                                                       .getService(ConfigService.class);
            searchableTagKeys = new HashSet<>(Arrays.asList(configService.getSearchableLogsTags().split(Const.COMMA)));
        }
        if (tags != null && !searchableTagKeys.containsAll(tags.stream().map(Tag::getKey).collect(toSet()))) {
            log.warn(
                "Searching tags that are not searchable: {}",
                tags.stream().map(Tag::getKey).filter(not(searchableTagKeys::contains)).collect(toSet()));
            return new Logs();
        }

        final var tables = tableHelper.getTablesForRead(
            LogRecord.INDEX_NAME,
            duration.getStartTimeBucket(),
            duration.getEndTimeBucket()
        );
        final var logs = new ArrayList<Log>();

        for (final var table : tables) {
            final var sqlAndParameters = buildSQL(
                serviceId, serviceInstanceId, endpointId, relatedTrace, queryOrder,
                from, limit, duration, tags, keywordsOfContent, excludingKeywordsOfContent, table);

            logs.addAll(
                jdbcClient.executeQuery(
                    sqlAndParameters.sql(),
                    this::parseResults,
                    sqlAndParameters.parameters()
                )
            );
        }
        final var comparator = Order.ASC.equals(queryOrder) ?
            comparing(Log::getTimestamp) :
            comparing(Log::getTimestamp).reversed();

        return new Logs(
            logs
                .stream()
                .sorted(comparator)
                .skip(from)
                .limit(limit)
                .collect(toList())
        );
    }

    protected ArrayList<Log> parseResults(ResultSet resultSet) throws SQLException {
        final var logs = new ArrayList<Log>();
        while (resultSet.next()) {
            Log log = new Log();
            log.setServiceId(resultSet.getString(SERVICE_ID));
            log.setServiceInstanceId(resultSet.getString(SERVICE_INSTANCE_ID));
            log.setEndpointId(resultSet.getString(ENDPOINT_ID));
            if (log.getEndpointId() != null) {
                log.setEndpointName(IDManager.EndpointID.analysisId(log.getEndpointId()).getEndpointName());
            }
            log.setTraceId(resultSet.getString(TRACE_ID));
            log.setTimestamp(resultSet.getLong(TIMESTAMP));
            log.setContentType(ContentType.instanceOf(resultSet.getInt(CONTENT_TYPE)));
            log.setContent(resultSet.getString(CONTENT));
            String dataBinaryBase64 = resultSet.getString(TAGS_RAW_DATA);
            if (!Strings.isNullOrEmpty(dataBinaryBase64)) {
                parserDataBinary(dataBinaryBase64, log.getTags());
            }
            logs.add(log);
        }

        return logs;
    }

    protected SQLAndParameters buildSQL(
        String serviceId,
        String serviceInstanceId,
        String endpointId,
        TraceScopeCondition relatedTrace,
        Order queryOrder,
        int from,
        int limit,
        final Duration duration,
        final List<Tag> tags,
        final List<String> keywordsOfContent,
        final List<String> excludingKeywordsOfContent,
        final String table) {
        long startSecondTB = 0;
        long endSecondTB = 0;
        if (nonNull(duration)) {
            startSecondTB = duration.getStartTimeBucketInSec();
            endSecondTB = duration.getEndTimeBucketInSec();
        }
        StringBuilder sql = new StringBuilder();
        List<Object> parameters = new ArrayList<>(10);

        sql.append("select * from ").append(table);
        /*
         * This is an AdditionalEntity feature, see:
         * {@link org.apache.skywalking.oap.server.core.storage.annotation.SQLDatabase.AdditionalEntity}
         */
        final var timeBucket = TableHelper.getTimeBucket(table);
        final var tagTable = TableHelper.getTable(AbstractLogRecord.ADDITIONAL_TAG_TABLE, timeBucket);
        if (!CollectionUtils.isEmpty(tags)) {
            for (int i = 0; i < tags.size(); i++) {
                sql.append(" inner join ").append(tagTable).append(" ");
                sql.append(tagTable + i);
                sql.append(" on ").append(table).append(".").append(JDBCTableInstaller.ID_COLUMN).append(" = ");
                sql.append(tagTable + i).append(".").append(JDBCTableInstaller.ID_COLUMN);
            }
        }
        sql.append(" where ");
        sql.append(JDBCTableInstaller.TABLE_COLUMN).append(" = ?");
        parameters.add(LogRecord.INDEX_NAME);
        if (startSecondTB != 0 && endSecondTB != 0) {
            sql.append(" and ").append(table).append(".").append(AbstractLogRecord.TIME_BUCKET).append(" >= ?");
            parameters.add(startSecondTB);
            sql.append(" and ").append(table).append(".").append(AbstractLogRecord.TIME_BUCKET).append(" <= ?");
            parameters.add(endSecondTB);
        }

        if (StringUtil.isNotEmpty(serviceId)) {
            sql.append(" and ").append(table).append(".").append(SERVICE_ID).append(" = ?");
            parameters.add(serviceId);
        }
        if (StringUtil.isNotEmpty(serviceInstanceId)) {
            sql.append(" and ").append(AbstractLogRecord.SERVICE_INSTANCE_ID).append(" = ?");
            parameters.add(serviceInstanceId);
        }
        if (StringUtil.isNotEmpty(endpointId)) {
            sql.append(" and ").append(AbstractLogRecord.ENDPOINT_ID).append(" = ?");
            parameters.add(endpointId);
        }
        if (nonNull(relatedTrace)) {
            if (StringUtil.isNotEmpty(relatedTrace.getTraceId())) {
                sql.append(" and ").append(TRACE_ID).append(" = ?");
                parameters.add(relatedTrace.getTraceId());
            }
            if (StringUtil.isNotEmpty(relatedTrace.getSegmentId())) {
                sql.append(" and ").append(TRACE_SEGMENT_ID).append(" = ?");
                parameters.add(relatedTrace.getSegmentId());
            }
            if (nonNull(relatedTrace.getSpanId())) {
                sql.append(" and ").append(SPAN_ID).append(" = ?");
                parameters.add(relatedTrace.getSpanId());
            }
        }

        if (CollectionUtils.isNotEmpty(tags)) {
            for (int i = 0; i < tags.size(); i++) {
                sql.append(" and ").append(tagTable + i).append(".");
                sql.append(AbstractLogRecord.TAGS).append(" = ?");
                parameters.add(tags.get(i).toString());
            }
        }
        sql.append(" order by ")
           .append(TIMESTAMP)
           .append(" ")
           .append(Order.DES.equals(queryOrder) ? "desc" : "asc");

        sql.append(" limit ").append(from + limit);

        return new SQLAndParameters(sql.toString(), parameters);
    }
}
