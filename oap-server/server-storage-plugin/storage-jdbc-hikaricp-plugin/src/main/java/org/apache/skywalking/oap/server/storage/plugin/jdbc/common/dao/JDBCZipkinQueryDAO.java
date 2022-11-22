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

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.RequiredArgsConstructor;
import static org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.H2TableInstaller.ID_COLUMN;
import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.storage.query.IZipkinQueryDAO;
import org.apache.skywalking.oap.server.core.zipkin.ZipkinServiceRelationTraffic;
import org.apache.skywalking.oap.server.core.zipkin.ZipkinServiceSpanTraffic;
import org.apache.skywalking.oap.server.core.zipkin.ZipkinServiceTraffic;
import org.apache.skywalking.oap.server.core.zipkin.ZipkinSpanRecord;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCHikariCPClient;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import zipkin2.Endpoint;
import zipkin2.Span;
import zipkin2.storage.QueryRequest;

@RequiredArgsConstructor
public class JDBCZipkinQueryDAO implements IZipkinQueryDAO {
    private final static int NAME_QUERY_MAX_SIZE = Integer.MAX_VALUE;
    private static final Gson GSON = new Gson();

    private final JDBCHikariCPClient h2Client;

    @Override
    public List<String> getServiceNames() throws IOException {
        StringBuilder sql = new StringBuilder();
        sql.append("select ").append(ZipkinServiceTraffic.SERVICE_NAME).append(" from ").append(ZipkinServiceTraffic.INDEX_NAME);
        sql.append(" where ").append("1=1");
        sql.append(" limit ").append(NAME_QUERY_MAX_SIZE);
        try (Connection connection = h2Client.getConnection()) {
            ResultSet resultSet = h2Client.executeQuery(connection, sql.toString());
            List<String> services = new ArrayList<>();
            while (resultSet.next()) {
                services.add(resultSet.getString(ZipkinServiceTraffic.SERVICE_NAME));
            }
            return services;
        } catch (SQLException e) {
            throw new IOException(e);
        }
    }

    @Override
    public List<String> getRemoteServiceNames(final String serviceName) throws IOException {
        StringBuilder sql = new StringBuilder();
        List<Object> condition = new ArrayList<>(1);
        sql.append("select ").append(ZipkinServiceRelationTraffic.REMOTE_SERVICE_NAME).append(" from ")
           .append(ZipkinServiceRelationTraffic.INDEX_NAME);
        sql.append(" where ");
        sql.append(ZipkinServiceRelationTraffic.SERVICE_NAME).append(" = ?");
        sql.append(" limit ").append(NAME_QUERY_MAX_SIZE);
        condition.add(serviceName);
        try (Connection connection = h2Client.getConnection()) {
            ResultSet resultSet = h2Client.executeQuery(connection, sql.toString(), condition.toArray(new Object[0]));
            List<String> remoteServices = new ArrayList<>();
            while (resultSet.next()) {
                remoteServices.add(resultSet.getString(ZipkinServiceRelationTraffic.REMOTE_SERVICE_NAME));
            }
            return remoteServices;
        } catch (SQLException e) {
            throw new IOException(e);
        }
    }

    @Override
    public List<String> getSpanNames(final String serviceName) throws IOException {
        StringBuilder sql = new StringBuilder();
        List<Object> condition = new ArrayList<>(1);
        sql.append("select ").append(ZipkinServiceSpanTraffic.SPAN_NAME).append(" from ")
           .append(ZipkinServiceSpanTraffic.INDEX_NAME);
        sql.append(" where ");
        sql.append(ZipkinServiceSpanTraffic.SERVICE_NAME).append(" = ?");
        sql.append(" limit ").append(NAME_QUERY_MAX_SIZE);
        condition.add(serviceName);
        try (Connection connection = h2Client.getConnection()) {
            ResultSet resultSet = h2Client.executeQuery(connection, sql.toString(), condition.toArray(new Object[0]));
            List<String> spanNames = new ArrayList<>();
            while (resultSet.next()) {
                spanNames.add(resultSet.getString(ZipkinServiceSpanTraffic.SPAN_NAME));
            }
            return spanNames;
        } catch (SQLException e) {
            throw new IOException(e);
        }
    }

    @Override
    public List<Span> getTrace(final String traceId) throws IOException {
        StringBuilder sql = new StringBuilder();
        List<Object> condition = new ArrayList<>(1);
        sql.append("select * from ").append(ZipkinSpanRecord.INDEX_NAME);
        sql.append(" where ");
        sql.append(ZipkinSpanRecord.TRACE_ID).append(" = ?");
        condition.add(traceId);
        try (Connection connection = h2Client.getConnection()) {
            ResultSet resultSet = h2Client.executeQuery(connection, sql.toString(), condition.toArray(new Object[0]));
            List<Span> trace = new ArrayList<>();
            while (resultSet.next()) {
                trace.add(buildSpan(resultSet));
            }
            return trace;
        } catch (SQLException e) {
            throw new IOException(e);
        }
    }

    @Override
    public List<List<Span>> getTraces(final QueryRequest request, Duration duration) throws IOException {
        final long startTimeMillis = duration.getStartTimestamp();
        final long endTimeMillis = duration.getEndTimestamp();
        StringBuilder sql = new StringBuilder();
        List<Object> condition = new ArrayList<>(5);
        List<Map.Entry<String, String>> annotations = new ArrayList<>(request.annotationQuery().entrySet());
        sql.append("select ").append(ZipkinSpanRecord.INDEX_NAME).append(".").append(ZipkinSpanRecord.TRACE_ID).append(", ")
            .append("min(").append(ZipkinSpanRecord.INDEX_NAME).append(".").append(ZipkinSpanRecord.TIMESTAMP_MILLIS).append(")").append(" from ");
        sql.append(ZipkinSpanRecord.INDEX_NAME);
        /**
         * This is an AdditionalEntity feature, see:
         * {@link org.apache.skywalking.oap.server.core.storage.annotation.SQLDatabase.AdditionalEntity}
         */
        if (!CollectionUtils.isEmpty(annotations)) {
            for (int i = 0; i < annotations.size(); i++) {
                sql.append(" inner join ").append(ZipkinSpanRecord.ADDITIONAL_QUERY_TABLE).append(" ");
                sql.append(ZipkinSpanRecord.ADDITIONAL_QUERY_TABLE + i);
                sql.append(" on ").append(ZipkinSpanRecord.INDEX_NAME).append(".").append(ID_COLUMN).append(" = ");
                sql.append(ZipkinSpanRecord.ADDITIONAL_QUERY_TABLE + i).append(".").append(ID_COLUMN);
            }
        }
        sql.append(" where ");
        sql.append(" 1=1 ");
        if (startTimeMillis > 0 && endTimeMillis > 0) {
            sql.append(" and ");
            sql.append(ZipkinSpanRecord.TIMESTAMP_MILLIS).append(" >= ?");
            condition.add(startTimeMillis);
            sql.append(" and ");
            sql.append(ZipkinSpanRecord.TIMESTAMP_MILLIS).append(" <= ?");
            condition.add(endTimeMillis);
            buildShardingCondition(sql, condition, startTimeMillis, endTimeMillis);
        }
        if (request.minDuration() != null) {
            sql.append(" and ");
            sql.append(ZipkinSpanRecord.DURATION).append(" >= ?");
            condition.add(request.minDuration());
        }
        if (request.maxDuration() != null) {
            sql.append(" and ");
            sql.append(ZipkinSpanRecord.DURATION).append(" <= ?");
            condition.add(request.maxDuration());
        }
        if (!StringUtil.isEmpty(request.serviceName())) {
            sql.append(" and ");
            sql.append(ZipkinSpanRecord.LOCAL_ENDPOINT_SERVICE_NAME).append(" = ?");
            condition.add(request.serviceName());
        }
        if (!StringUtil.isEmpty(request.remoteServiceName())) {
            sql.append(" and ");
            sql.append(ZipkinSpanRecord.REMOTE_ENDPOINT_SERVICE_NAME).append(" = ?");
            condition.add(request.remoteServiceName());
        }
        if (!StringUtil.isEmpty(request.spanName())) {
            sql.append(" and ");
            sql.append(ZipkinSpanRecord.NAME).append(" = ?");
            condition.add(request.spanName());
        }
        if (CollectionUtils.isNotEmpty(annotations)) {
            for (int i = 0; i < annotations.size(); i++) {
                Map.Entry<String, String> annotation = annotations.get(i);
                if (annotation.getValue().isEmpty()) {
                    sql.append(" and ").append(ZipkinSpanRecord.ADDITIONAL_QUERY_TABLE).append(i).append(".");
                    sql.append(ZipkinSpanRecord.QUERY).append(" = ?");
                    condition.add(annotation.getKey());
                } else {
                    sql.append(" and ").append(ZipkinSpanRecord.ADDITIONAL_QUERY_TABLE).append(i).append(".");
                    sql.append(ZipkinSpanRecord.QUERY).append(" = ?");
                    condition.add(annotation.getKey() + "=" + annotation.getValue());
                }
            }
        }
        sql.append(" group by ").append(ZipkinSpanRecord.TRACE_ID);
        sql.append(" order by min(").append(ZipkinSpanRecord.INDEX_NAME).append(".").append(ZipkinSpanRecord.TIMESTAMP_MILLIS).append(") desc");
        sql.append(" limit ").append(request.limit());
        Set<String> traceIds = new HashSet<>();
        try (Connection connection = h2Client.getConnection()) {
            ResultSet resultSet = h2Client.executeQuery(connection, sql.toString(), condition.toArray(new Object[0]));
            while (resultSet.next()) {
                traceIds.add(resultSet.getString(ZipkinSpanRecord.TRACE_ID));
            }
        } catch (SQLException e) {
            throw new IOException(e);
        }
        return getTraces(traceIds);
    }

    @Override
    public List<List<Span>> getTraces(final Set<String> traceIds) throws IOException {
        if (CollectionUtils.isEmpty(traceIds)) {
            return new ArrayList<>();
        }
        StringBuilder sql = new StringBuilder();
        List<Object> condition = new ArrayList<>(5);
        sql.append("select * from ").append(ZipkinSpanRecord.INDEX_NAME);
        sql.append(" where ");
        sql.append(" 1=1 ");

        int i = 0;
        sql.append(" and ");
        for (final String traceId : traceIds) {
            sql.append(ZipkinSpanRecord.TRACE_ID).append(" = ?");
            condition.add(traceId);
            if (i != traceIds.size() - 1) {
                sql.append(" or ");
            }
            i++;
        }

        sql.append(" order by ").append(ZipkinSpanRecord.TIMESTAMP_MILLIS).append(" desc");

        try (Connection connection = h2Client.getConnection()) {
            ResultSet resultSet = h2Client.executeQuery(connection, sql.toString(), condition.toArray(new Object[0]));
            return buildTraces(resultSet);
        } catch (SQLException e) {
            throw new IOException(e);
        }
    }

    private List<List<Span>> buildTraces(ResultSet resultSet) throws SQLException {
        Map<String, List<Span>> groupedByTraceId = new LinkedHashMap<String, List<Span>>();
        while (resultSet.next()) {
            Span span = buildSpan(resultSet);
            String traceId = span.traceId();
            groupedByTraceId.putIfAbsent(traceId, new ArrayList<>());
            groupedByTraceId.get(traceId).add(span);
        }
        return new ArrayList<>(groupedByTraceId.values());
    }

    private Span buildSpan(ResultSet resultSet) throws SQLException {
        Span.Builder span = Span.newBuilder();
        span.traceId(resultSet.getString(ZipkinSpanRecord.TRACE_ID));
        span.id(resultSet.getString(ZipkinSpanRecord.SPAN_ID));
        span.parentId(resultSet.getString(ZipkinSpanRecord.PARENT_ID));
        span.kind(Span.Kind.valueOf(resultSet.getString(ZipkinSpanRecord.KIND)));
        span.timestamp(resultSet.getLong(ZipkinSpanRecord.TIMESTAMP));
        span.duration(resultSet.getLong(ZipkinSpanRecord.DURATION));
        span.name(resultSet.getString(ZipkinSpanRecord.NAME));

        if (resultSet.getString(ZipkinSpanRecord.DEBUG) != null) {
            span.debug(Boolean.TRUE);
        }
        if (resultSet.getString(ZipkinSpanRecord.SHARED) != null) {
            span.shared(Boolean.TRUE);
        }
        //Build localEndpoint
        Endpoint.Builder localEndpoint = Endpoint.newBuilder();
        localEndpoint.serviceName(resultSet.getString(ZipkinSpanRecord.LOCAL_ENDPOINT_SERVICE_NAME));
        if (!StringUtil.isEmpty(resultSet.getString(ZipkinSpanRecord.LOCAL_ENDPOINT_IPV4))) {
            localEndpoint.parseIp(resultSet.getString(ZipkinSpanRecord.LOCAL_ENDPOINT_IPV4));
        } else {
            localEndpoint.parseIp(resultSet.getString(ZipkinSpanRecord.LOCAL_ENDPOINT_IPV6));
        }
        localEndpoint.port(resultSet.getInt(ZipkinSpanRecord.LOCAL_ENDPOINT_PORT));
        span.localEndpoint(localEndpoint.build());
        //Build remoteEndpoint
        Endpoint.Builder remoteEndpoint = Endpoint.newBuilder();
        remoteEndpoint.serviceName(resultSet.getString(ZipkinSpanRecord.REMOTE_ENDPOINT_SERVICE_NAME));
        if (!StringUtil.isEmpty(resultSet.getString(ZipkinSpanRecord.REMOTE_ENDPOINT_IPV4))) {
            remoteEndpoint.parseIp(resultSet.getString(ZipkinSpanRecord.REMOTE_ENDPOINT_IPV4));
        } else {
            remoteEndpoint.parseIp(resultSet.getString(ZipkinSpanRecord.REMOTE_ENDPOINT_IPV6));
        }
        remoteEndpoint.port(resultSet.getInt(ZipkinSpanRecord.REMOTE_ENDPOINT_PORT));
        span.remoteEndpoint(remoteEndpoint.build());

        //Build tags
        String tagsString = resultSet.getString(ZipkinSpanRecord.TAGS);
        if (!StringUtil.isEmpty(tagsString)) {
            JsonObject tagsJson = GSON.fromJson(tagsString, JsonObject.class);
            for (Map.Entry<String, JsonElement> tag : tagsJson.entrySet()) {
                span.putTag(tag.getKey(), tag.getValue().getAsString());
            }
        }
        //Build annotation
        String annotationString = resultSet.getString(ZipkinSpanRecord.ANNOTATIONS);
        if (!StringUtil.isEmpty(annotationString)) {
            JsonObject annotationJson = GSON.fromJson(annotationString, JsonObject.class);
            for (Map.Entry<String, JsonElement> annotation : annotationJson.entrySet()) {
                span.addAnnotation(Long.parseLong(annotation.getKey()), annotation.getValue().getAsString());
            }
        }
        return span.build();
    }

    protected void buildShardingCondition(StringBuilder sql, List<Object> parameters, long startTimeMillis, long endTimeMillis) {
    }
}
