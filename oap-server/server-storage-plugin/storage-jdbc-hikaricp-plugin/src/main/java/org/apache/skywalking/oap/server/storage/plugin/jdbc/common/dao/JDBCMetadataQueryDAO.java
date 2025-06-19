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
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.linecorp.armeria.common.annotation.Nullable;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.manual.endpoint.EndpointTraffic;
import org.apache.skywalking.oap.server.core.analysis.manual.instance.InstanceTraffic;
import org.apache.skywalking.oap.server.core.analysis.manual.process.ProcessDetectType;
import org.apache.skywalking.oap.server.core.analysis.manual.process.ProcessTraffic;
import org.apache.skywalking.oap.server.core.analysis.manual.service.ServiceTraffic;
import org.apache.skywalking.oap.server.core.query.enumeration.Language;
import org.apache.skywalking.oap.server.core.query.enumeration.ProfilingSupportStatus;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.type.Attribute;
import org.apache.skywalking.oap.server.core.query.type.Endpoint;
import org.apache.skywalking.oap.server.core.query.type.Process;
import org.apache.skywalking.oap.server.core.query.type.Service;
import org.apache.skywalking.oap.server.core.query.type.ServiceInstance;
import org.apache.skywalking.oap.server.core.storage.query.IMetadataQueryDAO;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCClient;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.JDBCEntityConverters;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.JDBCTableInstaller;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.SQLAndParameters;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.common.TableHelper;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.toList;
import static org.apache.skywalking.oap.server.core.analysis.manual.instance.InstanceTraffic.PropertyUtil.LANGUAGE;

public class JDBCMetadataQueryDAO implements IMetadataQueryDAO {
    private static final Gson GSON = new Gson();

    private final JDBCClient jdbcClient;
    private final int metadataQueryMaxSize;
    private final TableHelper tableHelper;

    public JDBCMetadataQueryDAO(JDBCClient jdbcClient, int metadataQueryMaxSize, ModuleManager moduleManager) {
        this.jdbcClient = jdbcClient;
        this.metadataQueryMaxSize = metadataQueryMaxSize;
        this.tableHelper = new TableHelper(moduleManager, jdbcClient);
    }

    @Override
    @SneakyThrows
    public List<Service> listServices() {
        final var results = new ArrayList<Service>();
        final var tables = tableHelper.getTablesWithinTTL(ServiceTraffic.INDEX_NAME);

        for (final var table : tables) {
            final var sqlAndParameters = buildSQLForListServices(table);
            results.addAll(jdbcClient.executeQuery(
                sqlAndParameters.sql(),
                this::buildServices,
                sqlAndParameters.parameters())
            );
        }
        return results
            .stream()
            .limit(metadataQueryMaxSize)
            .collect(toList());
    }

    protected SQLAndParameters buildSQLForListServices(String table) {
        final var sql = new StringBuilder();
        final var parameters = new ArrayList<>(5);
        sql.append("select * from ").append(table)
           .append(" where ").append(JDBCTableInstaller.TABLE_COLUMN).append(" = ? ");
        parameters.add(ServiceTraffic.INDEX_NAME);

        sql.append(" limit ").append(metadataQueryMaxSize);

        return new SQLAndParameters(sql.toString(), parameters);
    }

    @Override
    @SneakyThrows
    public List<ServiceInstance> listInstances(@Nullable Duration duration,
                                               String serviceId) {
        final var results = new ArrayList<ServiceInstance>();
        final var tables = tableHelper.getTablesWithinTTL(InstanceTraffic.INDEX_NAME);
        for (String table : tables) {
            final var sqlAndParameters = buildSQLForListInstances(serviceId, duration, table);
            results.addAll(
                jdbcClient.executeQuery(
                    sqlAndParameters.sql(),
                    this::buildInstances,
                    sqlAndParameters.parameters()
                )
            );
        }

        return results
            .stream()
            .limit(metadataQueryMaxSize)
            .collect(toList());
    }

    protected SQLAndParameters buildSQLForListInstances(String serviceId, Duration duration, String table) {
        final var  sql = new StringBuilder();
        final var parameters = new ArrayList<>(5);
        sql.append("select * from ").append(table).append(" where ")
            .append(JDBCTableInstaller.TABLE_COLUMN).append(" = ?");
        parameters.add(InstanceTraffic.INDEX_NAME);
        if (duration != null) {
            final var startMinuteTimeBucket = TimeBucket.getMinuteTimeBucket(duration.getStartTimestamp());
            final var endMinuteTimeBucket = TimeBucket.getMinuteTimeBucket(duration.getEndTimestamp());
            sql.append(" and ").append(InstanceTraffic.LAST_PING_TIME_BUCKET).append(" >= ?");
            parameters.add(startMinuteTimeBucket);
            sql.append(" and ").append(InstanceTraffic.TIME_BUCKET).append(" <= ?");
            parameters.add(endMinuteTimeBucket);
        }
        sql.append(" and ").append(InstanceTraffic.SERVICE_ID).append("=?");
        parameters.add(serviceId);
        sql.append(" limit ").append(metadataQueryMaxSize);

        return new SQLAndParameters(sql.toString(), parameters);
    }

    @Override
    @SneakyThrows
    public ServiceInstance getInstance(final String instanceId) {
        final var tables = tableHelper.getTablesWithinTTL(InstanceTraffic.INDEX_NAME);

        for (String table : tables) {
            StringBuilder sql = new StringBuilder();
            List<Object> condition = new ArrayList<>(5);
            sql.append("select * from ").append(table).append(" where ")
                .append(JDBCTableInstaller.TABLE_COLUMN).append(" = ?");
            condition.add(InstanceTraffic.INDEX_NAME);
            sql.append(" and ").append(JDBCTableInstaller.ID_COLUMN).append(" = ?");
            condition.add(instanceId);
            sql.append(" limit ").append(metadataQueryMaxSize);

            final var result = jdbcClient.executeQuery(sql.toString(), resultSet -> {
                final List<ServiceInstance> instances = buildInstances(resultSet);
                return instances.size() > 0 ? instances.get(0) : null;
            }, condition.toArray(new Object[0]));
            if (result != null) {
                return result;
            }
        }

        return null;
    }

    @SneakyThrows
    @Override
    public List<ServiceInstance> getInstances(List<String> instanceIds) throws IOException {
        final var tables = tableHelper.getTablesWithinTTL(InstanceTraffic.INDEX_NAME);

        for (String table : tables) {
            StringBuilder sql = new StringBuilder();
            List<Object> condition = new ArrayList<>(5);
            sql.append("select * from ").append(table).append(" where ")
                .append(JDBCTableInstaller.TABLE_COLUMN).append(" = ?");
            condition.add(InstanceTraffic.INDEX_NAME);
            sql.append(" and ").append(JDBCTableInstaller.ID_COLUMN).append(" in ")
               .append(
                   instanceIds.stream().map(instanceId -> "?").collect(Collectors.joining(",", "(", ")"))
               );
            condition.addAll(instanceIds);
            sql.append(" limit ").append(instanceIds.size());

            final var result = jdbcClient.executeQuery(sql.toString(), resultSet -> buildInstances(resultSet), condition.toArray(new Object[0]));
            if (result != null) {
                return result;
            }
        }

        return null;
    }

    @Override
    @SneakyThrows
    public List<Endpoint> findEndpoint(String keyword, String serviceId, int limit, Duration duration) {
        final var results = new ArrayList<Endpoint>();
        final var tables = tableHelper.getTablesWithinTTL(EndpointTraffic.INDEX_NAME);

        for (String table : tables) {
            StringBuilder sql = new StringBuilder();
            List<Object> condition = new ArrayList<>(5);
            sql.append("select * from ").append(table).append(" where ")
                .append(JDBCTableInstaller.TABLE_COLUMN).append(" = ?");
            condition.add(EndpointTraffic.INDEX_NAME);
            sql.append(" and ").append(EndpointTraffic.SERVICE_ID).append("=?");
            condition.add(serviceId);
            sql.append(" and ").append(JDBCTableInstaller.TABLE_COLUMN).append(" = ?");
            condition.add(EndpointTraffic.INDEX_NAME);
            if (!Strings.isNullOrEmpty(keyword)) {
                sql.append(" and ").append(EndpointTraffic.NAME).append(" like concat('%',?,'%') ");
                condition.add(keyword);
            }
            if (duration != null) {
                final var startMinuteTimeBucket = TimeBucket.getMinuteTimeBucket(duration.getStartTimestamp());
                final var endMinuteTimeBucket = TimeBucket.getMinuteTimeBucket(duration.getEndTimestamp());
                sql.append(" and ").append(EndpointTraffic.LAST_PING_TIME_BUCKET).append(" >= ?");
                condition.add(startMinuteTimeBucket);
                sql.append(" and ").append(EndpointTraffic.TIME_BUCKET).append(" <= ?");
                condition.add(endMinuteTimeBucket);
            }
            sql.append(" order by ").append(EndpointTraffic.TIME_BUCKET).append(" desc");
            sql.append(" limit ").append(limit);

            results.addAll(
                jdbcClient.executeQuery(
                    sql.toString(), resultSet -> {
                        List<Endpoint> endpoints = new ArrayList<>();

                        while (resultSet.next()) {
                            final var endpointTraffic = new EndpointTraffic.Builder().storage2Entity(JDBCEntityConverters.toEntity(resultSet));

                            Endpoint endpoint = new Endpoint();
                            endpoint.setId(endpointTraffic.id().build());
                            endpoint.setName(endpointTraffic.getName());
                            endpoints.add(endpoint);
                        }
                        return endpoints;
                    }, condition.toArray(new Object[0])));
        }
        return results.stream().limit(limit).collect(toList());
    }

    @Override
    @SneakyThrows
    public List<Process> listProcesses(String serviceId, ProfilingSupportStatus supportStatus, long lastPingStartTimeBucket, long lastPingEndTimeBucket) {
        List<String> tables = tableHelper.getTablesWithinTTL(ProcessTraffic.INDEX_NAME);
        final var results = new ArrayList<Process>();

        for (String table : tables) {
            final var sqlAndParameters = buildSQLForListProcesses(serviceId, supportStatus, lastPingStartTimeBucket, lastPingEndTimeBucket, table);
            results.addAll(
                jdbcClient.executeQuery(
                    sqlAndParameters.sql(),
                    this::buildProcesses,
                    sqlAndParameters.parameters()
                )
            );
        }

        return results
            .stream()
            .limit(metadataQueryMaxSize)
            .collect(toList());
    }

    protected SQLAndParameters buildSQLForListProcesses(
        final String serviceId,
        final ProfilingSupportStatus supportStatus,
        final long lastPingStartTimeBucket,
        final long lastPingEndTimeBucket,
        final String table) {
        final var sql = new StringBuilder();
        final var parameters = new ArrayList<>();
        sql.append("select * from ").append(table);
        sql.append(" where ").append(JDBCTableInstaller.TABLE_COLUMN).append(" = ? ");
        parameters.add(ProcessTraffic.INDEX_NAME);
        appendProcessWhereQuery(sql, parameters, serviceId, null, null, supportStatus, lastPingStartTimeBucket, lastPingEndTimeBucket, false);
        sql.append(" limit ").append(metadataQueryMaxSize);

        return new SQLAndParameters(sql.toString(), parameters);
    }

    @Override
    @SneakyThrows
    public List<Process> listProcesses(String serviceInstanceId, Duration duration, boolean includeVirtual) {
        final List<String> tables = tableHelper.getTablesWithinTTL(ProcessTraffic.INDEX_NAME);
        final var results = new ArrayList<Process>();

        for (String table : tables) {
            final var sqlAndParameters = buildSQLForListProcesses(serviceInstanceId, duration, includeVirtual, table);

            results.addAll(
                jdbcClient.executeQuery(
                    sqlAndParameters.sql(),
                    this::buildProcesses,
                    sqlAndParameters.parameters()
                )
            );
        }

        return results
            .stream()
            .limit(metadataQueryMaxSize)
            .collect(toList());
    }

    protected SQLAndParameters buildSQLForListProcesses(String serviceInstanceId, Duration duration, boolean includeVirtual, String table) {
        final var lastPingStartTimeBucket = duration.getStartTimeBucket();
        final var lastPingEndTimeBucket = duration.getEndTimeBucket();
        final var sql = new StringBuilder();
        final var condition = new ArrayList<>();
        sql.append("select * from ").append(table);
        sql.append(" where ").append(JDBCTableInstaller.TABLE_COLUMN).append(" = ? ");
        condition.add(ProcessTraffic.INDEX_NAME);
        appendProcessWhereQuery(sql, condition, null, serviceInstanceId, null, null, lastPingStartTimeBucket, lastPingEndTimeBucket, includeVirtual);
        sql.append(" limit ").append(metadataQueryMaxSize);
        return new SQLAndParameters(sql.toString(), condition);
    }

    @Override
    @SneakyThrows
    public List<Process> listProcesses(String agentId, long startPingTimeBucket, long endPingTimeBucket) {
        final var tables = tableHelper.getTablesWithinTTL(ProcessTraffic.INDEX_NAME);
        final var results = new ArrayList<Process>();

        for (String table : tables) {
            StringBuilder sql = new StringBuilder();
            List<Object> condition = new ArrayList<>(2);
            sql.append("select * from ").append(table);
            sql.append(" where ").append(JDBCTableInstaller.TABLE_COLUMN).append(" = ? ");
            condition.add(ProcessTraffic.INDEX_NAME);
            appendProcessWhereQuery(sql, condition, null, null, agentId, null, 0, 0, false);
            sql.append(" limit ").append(metadataQueryMaxSize);

            results.addAll(
                jdbcClient.executeQuery(sql.toString(), this::buildProcesses, condition.toArray(new Object[0]))
            );
        }

        return results
            .stream()
            .limit(metadataQueryMaxSize)
            .collect(toList());
    }

    @Override
    @SneakyThrows
    public long getProcessCount(String serviceId, ProfilingSupportStatus profilingSupportStatus, long lastPingStartTimeBucket, long lastPingEndTimeBucket) {
        final var tables = tableHelper.getTablesWithinTTL(ProcessTraffic.INDEX_NAME);
        long total = 0;

        for (String table : tables) {
            StringBuilder sql = new StringBuilder();
            List<Object> condition = new ArrayList<>(5);
            sql.append("select count(1) total from ").append(table);
            sql.append(" where ").append(JDBCTableInstaller.TABLE_COLUMN).append(" = ? ");
            condition.add(ProcessTraffic.INDEX_NAME);

            appendProcessWhereQuery(sql, condition, serviceId, null, null, profilingSupportStatus,
                lastPingStartTimeBucket, lastPingEndTimeBucket, false);

            total += jdbcClient.executeQuery(sql.toString(), resultSet -> {
                if (!resultSet.next()) {
                    return 0L;
                }
                return resultSet.getLong("total");
            }, condition.toArray(new Object[0]));
        }

        return total;
    }

    @Override
    @SneakyThrows
    public long getProcessCount(String instanceId) {
        final var tables = tableHelper.getTablesWithinTTL(ProcessTraffic.INDEX_NAME);
        long total = 0;

        for (String table : tables) {
            StringBuilder sql = new StringBuilder();
            List<Object> condition = new ArrayList<>(3);
            sql.append("select count(1) total from ").append(table);
            sql.append(" where ").append(JDBCTableInstaller.TABLE_COLUMN).append(" = ? ");
            condition.add(ProcessTraffic.INDEX_NAME);

            appendProcessWhereQuery(sql, condition, null, instanceId, null, null, 0, 0, false);

            total += jdbcClient.executeQuery(sql.toString(), resultSet -> {
                if (!resultSet.next()) {
                    return 0L;
                }
                return resultSet.getLong("total");
            }, condition.toArray(new Object[0]));
        }

        return total;
    }

    private List<Service> buildServices(ResultSet resultSet) throws SQLException {
        List<Service> services = new ArrayList<>();
        while (resultSet.next()) {
            final var serviceTraffic = new ServiceTraffic.Builder().storage2Entity(JDBCEntityConverters.toEntity(resultSet));

            String serviceName = serviceTraffic.getName();
            Service service = new Service();
            service.setId(serviceTraffic.getServiceId());
            service.setName(serviceName);
            service.setShortName(serviceTraffic.getShortName());
            service.setGroup(serviceTraffic.getGroup());
            service.getLayers().add(serviceTraffic.getLayer().name());
            services.add(service);
        }
        return services;
    }

    private List<ServiceInstance> buildInstances(ResultSet resultSet) throws SQLException {
        List<ServiceInstance> serviceInstances = new ArrayList<>();

        while (resultSet.next()) {
            final var instanceTraffic =
                new InstanceTraffic.Builder().storage2Entity(JDBCEntityConverters.toEntity(resultSet));

            ServiceInstance serviceInstance = new ServiceInstance();
            serviceInstance.setId(instanceTraffic.id().build());
            serviceInstance.setName(instanceTraffic.getName());
            serviceInstance.setInstanceUUID(serviceInstance.getId());

            JsonObject properties = instanceTraffic.getProperties();
            if (properties != null) {
                for (Map.Entry<String, JsonElement> property : properties.entrySet()) {
                    String key = property.getKey();
                    String value = property.getValue().getAsString();
                    if (key.equals(LANGUAGE)) {
                        serviceInstance.setLanguage(Language.value(value));
                    } else {
                        serviceInstance.getAttributes().add(new Attribute(key, value));
                    }
                }
            } else {
                serviceInstance.setLanguage(Language.UNKNOWN);
            }
            serviceInstances.add(serviceInstance);
        }
        return serviceInstances;
    }

    private void appendProcessWhereQuery(StringBuilder sql, List<Object> condition, String serviceId, String instanceId,
                                         String agentId, final ProfilingSupportStatus profilingSupportStatus,
                                         final long lastPingStartTimeBucket, final long lastPingEndTimeBucket,
                                         boolean includeVirtual) {
        if (StringUtil.isNotEmpty(serviceId)) {
            sql.append(" and ").append(ProcessTraffic.SERVICE_ID).append("=?");
            condition.add(serviceId);
        }
        if (StringUtil.isNotEmpty(instanceId)) {
            if (!condition.isEmpty()) {
                sql.append(" and ");
            }
            sql.append(ProcessTraffic.INSTANCE_ID).append("=?");
            condition.add(instanceId);
        }
        if (StringUtil.isNotEmpty(agentId)) {
            if (!condition.isEmpty()) {
                sql.append(" and ");
            }
            sql.append(ProcessTraffic.AGENT_ID).append("=?");
            condition.add(agentId);
        }
        if (profilingSupportStatus != null) {
            if (!condition.isEmpty()) {
                sql.append(" and ");
            }
            sql.append(ProcessTraffic.PROFILING_SUPPORT_STATUS).append("=?");
            condition.add(profilingSupportStatus.value());
        }
        if (lastPingStartTimeBucket > 0) {
            if (!condition.isEmpty()) {
                sql.append(" and ");
            }
            sql.append(ProcessTraffic.LAST_PING_TIME_BUCKET).append(">=?");
            condition.add(lastPingStartTimeBucket);
        }
        if (lastPingEndTimeBucket > 0) {
            if (!condition.isEmpty()) {
                sql.append(" and ");
            }
            sql.append(ProcessTraffic.TIME_BUCKET).append("<=?");
            condition.add(lastPingEndTimeBucket);
        }
        if (!includeVirtual) {
            if (!condition.isEmpty()) {
                sql.append(" and ");
            }
            sql.append(ProcessTraffic.DETECT_TYPE).append("!=?");
            condition.add(ProcessDetectType.VIRTUAL.value());
        }
    }

    @Override
    @SneakyThrows
    public Process getProcess(String processId) {
        final var tables = tableHelper.getTablesWithinTTL(ProcessTraffic.INDEX_NAME);

        for (String table : tables) {
            StringBuilder sql = new StringBuilder();
            List<Object> condition = new ArrayList<>(5);
            sql.append("select * from ").append(table).append(" where ");
            sql.append(JDBCTableInstaller.TABLE_COLUMN).append(" = ? ");
            condition.add(ProcessTraffic.INDEX_NAME);
            sql.append(" and ").append(JDBCTableInstaller.ID_COLUMN).append(" = ?");
            condition.add(processId);
            sql.append(" limit ").append(metadataQueryMaxSize);

            final var result = jdbcClient.executeQuery(
                sql.toString(),
                resultSet -> {
                    final List<Process> processes = buildProcesses(resultSet);
                    return processes.size() > 0 ? processes.get(0) : null;
                },
                condition.toArray(new Object[0]));
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    private List<Process> buildProcesses(ResultSet resultSet) throws SQLException {
        List<Process> processes = new ArrayList<>();
        while (resultSet.next()) {
            final var processTraffic = new ProcessTraffic.Builder().storage2Entity(JDBCEntityConverters.toEntity(resultSet));

            Process process = new Process();
            process.setId(processTraffic.id().build());
            process.setName(processTraffic.getName());
            final String serviceId = processTraffic.getServiceId();
            process.setServiceId(serviceId);
            process.setServiceName(IDManager.ServiceID.analysisId(serviceId).getName());
            final String instanceId = processTraffic.getInstanceId();
            process.setInstanceId(instanceId);
            process.setInstanceName(IDManager.ServiceInstanceID.analysisId(instanceId).getName());
            process.setAgentId(processTraffic.getAgentId());
            process.setDetectType(ProcessDetectType.valueOf(processTraffic.getDetectType()).name());
            process.setProfilingSupportStatus(ProfilingSupportStatus.valueOf(processTraffic.getProfilingSupportStatus()).name());

            JsonObject properties = processTraffic.getProperties();
            if (properties != null) {
                for (Map.Entry<String, JsonElement> property : properties.entrySet()) {
                    String key = property.getKey();
                    String value = property.getValue().getAsString();
                    process.getAttributes().add(new Attribute(key, value));
                }
            }
            final String labelsJson = processTraffic.getLabelsJson();
            if (StringUtils.isNotEmpty(labelsJson)) {
                final List<String> labels = GSON.<List<String>>fromJson(labelsJson, ArrayList.class);
                process.getLabels().addAll(labels);
            }
            processes.add(process);
        }
        return processes;
    }
}
