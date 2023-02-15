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
import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.manual.log.AbstractLogRecord;
import org.apache.skywalking.oap.server.core.analysis.manual.log.LogRecord;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.Tag;
import org.apache.skywalking.oap.server.core.config.ConfigService;
import org.apache.skywalking.oap.server.core.query.enumeration.Order;
import org.apache.skywalking.oap.server.core.query.input.TraceScopeCondition;
import org.apache.skywalking.oap.server.core.query.type.ContentType;
import org.apache.skywalking.oap.server.core.query.type.Log;
import org.apache.skywalking.oap.server.core.query.type.Logs;
import org.apache.skywalking.oap.server.core.storage.query.ILogQueryDAO;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCHikariCPClient;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import static java.util.Objects.nonNull;
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
import static org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.H2TableInstaller.ID_COLUMN;

@RequiredArgsConstructor
public class JDBCLogQueryDAO implements ILogQueryDAO {
    private final JDBCHikariCPClient jdbcClient;
    private final ModuleManager manager;
    private List<String> searchableTagKeys;

    @Override
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
                          final List<String> excludingKeywordsOfContent) throws IOException {
        long startSecondTB = 0;
        long endSecondTB = 0;
        if (nonNull(duration)) {
            startSecondTB = duration.getStartTimeBucketInSec();
            endSecondTB = duration.getEndTimeBucketInSec();
        }
        if (searchableTagKeys == null) {
            final ConfigService configService = manager.find(CoreModule.NAME)
                                                       .provider()
                                                       .getService(ConfigService.class);
            searchableTagKeys = Arrays.asList(configService.getSearchableLogsTags().split(Const.COMMA));
        }
        StringBuilder sql = new StringBuilder();
        List<Object> parameters = new ArrayList<>(10);

        sql.append("from ").append(LogRecord.INDEX_NAME);
        /**
         * This is an AdditionalEntity feature, see:
         * {@link org.apache.skywalking.oap.server.core.storage.annotation.SQLDatabase.AdditionalEntity}
         */
        if (!CollectionUtils.isEmpty(tags)) {
            for (int i = 0; i < tags.size(); i++) {
                sql.append(" inner join ").append(AbstractLogRecord.ADDITIONAL_TAG_TABLE).append(" ");
                sql.append(AbstractLogRecord.ADDITIONAL_TAG_TABLE + i);
                sql.append(" on ").append(LogRecord.INDEX_NAME).append(".").append(ID_COLUMN).append(" = ");
                sql.append(AbstractLogRecord.ADDITIONAL_TAG_TABLE + i).append(".").append(ID_COLUMN);
            }
        }
        sql.append(" where ");
        sql.append(" 1=1 ");
        if (startSecondTB != 0 && endSecondTB != 0) {
            sql.append(" and ").append(LogRecord.INDEX_NAME).append(".").append(AbstractLogRecord.TIME_BUCKET).append(" >= ?");
            parameters.add(startSecondTB);
            sql.append(" and ").append(LogRecord.INDEX_NAME).append(".").append(AbstractLogRecord.TIME_BUCKET).append(" <= ?");
            parameters.add(endSecondTB);
        }

        if (StringUtil.isNotEmpty(serviceId)) {
            sql.append(" and ").append(LogRecord.INDEX_NAME).append(".").append(SERVICE_ID).append(" = ?");
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
                final int foundIdx = searchableTagKeys.indexOf(tags.get(i).getKey());
                if (foundIdx > -1) {
                    sql.append(" and ").append(AbstractLogRecord.ADDITIONAL_TAG_TABLE + i).append(".");
                    sql.append(AbstractLogRecord.TAGS).append(" = ?");
                    parameters.add(tags.get(i).toString());
                } else {
                    //If the tag is not searchable, but is required, then don't need to run the real query.
                    return new Logs();
                }
            }
        }

        sql.append(" order by ")
           .append(TIMESTAMP)
           .append(" ")
           .append(Order.DES.equals(queryOrder) ? "desc" : "asc");

        Logs logs = new Logs();
        try (Connection connection = jdbcClient.getConnection()) {

            buildLimit(sql, from, limit);

            try (ResultSet resultSet = jdbcClient.executeQuery(
                connection, "select * " + sql.toString(), parameters.toArray(new Object[0]))) {
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
                    logs.getLogs().add(log);
                }
            }
        } catch (SQLException e) {
            throw new IOException(e);
        }

        return logs;
    }

    protected void buildLimit(StringBuilder sql, int from, int limit) {
        sql.append(" LIMIT ").append(limit);
        sql.append(" OFFSET ").append(from);
    }
}
