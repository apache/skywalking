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

package org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao;

import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.manual.endpoint.EndpointTraffic;
import org.apache.skywalking.oap.server.core.analysis.manual.instance.InstanceTraffic;
import org.apache.skywalking.oap.server.core.analysis.manual.process.ProcessDetectType;
import org.apache.skywalking.oap.server.core.analysis.manual.process.ProcessTraffic;
import org.apache.skywalking.oap.server.core.analysis.manual.service.ServiceTraffic;
import org.apache.skywalking.oap.server.core.query.enumeration.Language;
import org.apache.skywalking.oap.server.core.query.type.Attribute;
import org.apache.skywalking.oap.server.core.query.type.Endpoint;
import org.apache.skywalking.oap.server.core.query.type.Process;
import org.apache.skywalking.oap.server.core.query.type.Service;
import org.apache.skywalking.oap.server.core.query.type.ServiceInstance;
import org.apache.skywalking.oap.server.core.storage.query.IMetadataQueryDAO;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCHikariCPClient;
import org.apache.skywalking.oap.server.library.util.StringUtil;

public class H2MetadataQueryDAO implements IMetadataQueryDAO {
    private static final Gson GSON = new Gson();

    private JDBCHikariCPClient h2Client;
    private int metadataQueryMaxSize;

    public H2MetadataQueryDAO(JDBCHikariCPClient h2Client, int metadataQueryMaxSize) {
        this.h2Client = h2Client;
        this.metadataQueryMaxSize = metadataQueryMaxSize;
    }

    @Override
    public List<Service> listServices(final String layer, final String group) throws IOException {
        StringBuilder sql = new StringBuilder();
        List<Object> condition = new ArrayList<>(5);
        sql.append("select * from ").append(ServiceTraffic.INDEX_NAME);
        if (StringUtil.isNotEmpty(layer) || StringUtil.isNotEmpty(group)) {
            sql.append(" where ");
        }

        if (StringUtil.isNotEmpty(layer)) {
            sql.append(ServiceTraffic.LAYER).append("=?");
            condition.add(Layer.valueOf(layer).value());
        }
        if (StringUtil.isNotEmpty(layer) && StringUtil.isNotEmpty(group)) {
            sql.append(" and ");
        }
        if (StringUtil.isNotEmpty(group)) {
            sql.append(ServiceTraffic.GROUP).append("=?");
            condition.add(group);
        }

        sql.append(" limit ").append(metadataQueryMaxSize);

        try (Connection connection = h2Client.getConnection()) {
            try (ResultSet resultSet = h2Client.executeQuery(
                connection, sql.toString(), condition.toArray(new Object[0]))) {
                return buildServices(resultSet);
            }
        } catch (SQLException e) {
            throw new IOException(e);
        }
    }

    @Override
    public List<Service> getServices(final String serviceId) throws IOException {
        StringBuilder sql = new StringBuilder();
        List<Object> condition = new ArrayList<>(5);
        sql.append("select * from ").append(ServiceTraffic.INDEX_NAME).append(" where ");
        sql.append(ServiceTraffic.SERVICE_ID).append(" = ?");
        condition.add(serviceId);
        sql.append(" limit ").append(metadataQueryMaxSize);

        try (Connection connection = h2Client.getConnection()) {
            ResultSet resultSet = h2Client.executeQuery(
                connection, sql.toString(), condition.toArray(new Object[0]));
            return buildServices(resultSet);
        } catch (SQLException e) {
            throw new IOException(e);
        }
    }

    @Override
    public List<ServiceInstance> listInstances(long startTimestamp, long endTimestamp,
                                               String serviceId) throws IOException {
        final long minuteTimeBucket = TimeBucket.getMinuteTimeBucket(startTimestamp);

        StringBuilder sql = new StringBuilder();
        List<Object> condition = new ArrayList<>(5);
        sql.append("select * from ").append(InstanceTraffic.INDEX_NAME).append(" where ");
        sql.append(InstanceTraffic.LAST_PING_TIME_BUCKET).append(" >= ?");
        condition.add(minuteTimeBucket);
        sql.append(" and ").append(InstanceTraffic.SERVICE_ID).append("=?");
        condition.add(serviceId);
        sql.append(" limit ").append(metadataQueryMaxSize);

        try (Connection connection = h2Client.getConnection()) {
            ResultSet resultSet = h2Client.executeQuery(
                connection, sql.toString(), condition.toArray(new Object[0]));
            return buildInstances(resultSet);

        } catch (SQLException e) {
            throw new IOException(e);
        }
    }

    @Override
    public ServiceInstance getInstance(final String instanceId) throws IOException {
        StringBuilder sql = new StringBuilder();
        List<Object> condition = new ArrayList<>(5);
        sql.append("select * from ").append(InstanceTraffic.INDEX_NAME).append(" where ");
        sql.append(H2TableInstaller.ID_COLUMN).append(" = ?");
        condition.add(instanceId);
        sql.append(" limit ").append(metadataQueryMaxSize);

        try (Connection connection = h2Client.getConnection()) {
            ResultSet resultSet = h2Client.executeQuery(
                connection, sql.toString(), condition.toArray(new Object[0]));
            final List<ServiceInstance> instances = buildInstances(resultSet);
            return instances.size() > 0 ? instances.get(0) : null;
        } catch (SQLException e) {
            throw new IOException(e);
        }
    }

    @Override
    public List<Endpoint> findEndpoint(String keyword, String serviceId, int limit) throws IOException {
        StringBuilder sql = new StringBuilder();
        List<Object> condition = new ArrayList<>(5);
        sql.append("select * from ").append(EndpointTraffic.INDEX_NAME).append(" where ");
        sql.append(EndpointTraffic.SERVICE_ID).append("=?");
        condition.add(serviceId);
        if (!Strings.isNullOrEmpty(keyword)) {
            sql.append(" and ").append(EndpointTraffic.NAME).append(" like concat('%',?,'%') ");
            condition.add(keyword);
        }
        sql.append(" limit ").append(limit);

        List<Endpoint> endpoints = new ArrayList<>();
        try (Connection connection = h2Client.getConnection()) {
            try (ResultSet resultSet = h2Client.executeQuery(
                connection, sql.toString(), condition.toArray(new Object[0]))) {

                while (resultSet.next()) {
                    Endpoint endpoint = new Endpoint();
                    endpoint.setId(resultSet.getString(H2TableInstaller.ID_COLUMN));
                    endpoint.setName(resultSet.getString(EndpointTraffic.NAME));
                    endpoints.add(endpoint);
                }
            }
        } catch (SQLException e) {
            throw new IOException(e);
        }
        return endpoints;
    }

    private List<Service> buildServices(ResultSet resultSet) throws SQLException {
        List<Service> services = new ArrayList<>();
        while (resultSet.next()) {
            String serviceName = resultSet.getString(ServiceTraffic.NAME);
            Service service = new Service();
            service.setId(resultSet.getString(ServiceTraffic.SERVICE_ID));
            service.setName(serviceName);
            service.setShortName(resultSet.getString(ServiceTraffic.SHORT_NAME));
            service.setGroup(resultSet.getString(ServiceTraffic.GROUP));
            service.getLayers().add(Layer.valueOf(resultSet.getInt(ServiceTraffic.LAYER)).name());
            services.add(service);
        }
        return services;
    }

    private List<ServiceInstance> buildInstances(ResultSet resultSet) throws SQLException {
        List<ServiceInstance> serviceInstances = new ArrayList<>();

        while (resultSet.next()) {
            ServiceInstance serviceInstance = new ServiceInstance();
            serviceInstance.setId(resultSet.getString(H2TableInstaller.ID_COLUMN));
            serviceInstance.setName(resultSet.getString(InstanceTraffic.NAME));
            serviceInstance.setInstanceUUID(serviceInstance.getId());
            serviceInstance.setLayer(Layer.valueOf(resultSet.getInt(ServiceTraffic.LAYER)).name());

            String propertiesString = resultSet.getString(InstanceTraffic.PROPERTIES);
            if (!Strings.isNullOrEmpty(propertiesString)) {
                JsonObject properties = GSON.fromJson(propertiesString, JsonObject.class);
                for (Map.Entry<String, JsonElement> property : properties.entrySet()) {
                    String key = property.getKey();
                    String value = property.getValue().getAsString();
                    if (key.equals(InstanceTraffic.PropertyUtil.LANGUAGE)) {
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

    @Override
    public List<Process> listProcesses(String serviceId, String instanceId, String agentId) throws IOException {
        StringBuilder sql = new StringBuilder();
        List<Object> condition = new ArrayList<>(5);
        sql.append("select * from ").append(ProcessTraffic.INDEX_NAME);
        if (StringUtil.isNotEmpty(serviceId) || StringUtil.isNotEmpty(instanceId) || StringUtil.isNotEmpty(agentId)) {
            sql.append(" where ");
        }

        if (StringUtil.isNotEmpty(serviceId)) {
            sql.append(ProcessTraffic.SERVICE_ID).append("=?");
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

        sql.append(" limit ").append(metadataQueryMaxSize);

        try (Connection connection = h2Client.getConnection()) {
            try (ResultSet resultSet = h2Client.executeQuery(
                    connection, sql.toString(), condition.toArray(new Object[0]))) {
                return buildProcesses(resultSet);
            }
        } catch (SQLException e) {
            throw new IOException(e);
        }
    }

    @Override
    public Process getProcess(String processId) throws IOException {
        StringBuilder sql = new StringBuilder();
        List<Object> condition = new ArrayList<>(5);
        sql.append("select * from ").append(ProcessTraffic.INDEX_NAME).append(" where ");
        sql.append(H2TableInstaller.ID_COLUMN).append(" = ?");
        condition.add(processId);
        sql.append(" limit ").append(metadataQueryMaxSize);

        try (Connection connection = h2Client.getConnection()) {
            ResultSet resultSet = h2Client.executeQuery(
                    connection, sql.toString(), condition.toArray(new Object[0]));
            final List<Process> processes = buildProcesses(resultSet);
            return processes.size() > 0 ? processes.get(0) : null;
        } catch (SQLException e) {
            throw new IOException(e);
        }
    }

    private List<Process> buildProcesses(ResultSet resultSet) throws SQLException {
        List<Process> processes = new ArrayList<>();
        while (resultSet.next()) {
            final Process process = new Process();
            process.setId(resultSet.getString(H2TableInstaller.ID_COLUMN));
            process.setName(resultSet.getString(ProcessTraffic.NAME));
            final String serviceId = resultSet.getString(ProcessTraffic.SERVICE_ID);
            process.setServiceId(serviceId);
            final IDManager.ServiceID.ServiceIDDefinition serviceIDDefinition = IDManager.ServiceID.analysisId(serviceId);
            process.setServiceName(serviceIDDefinition.getName());
            final String instanceId = resultSet.getString(ProcessTraffic.INSTANCE_ID);
            process.setInstanceId(instanceId);
            final IDManager.ServiceInstanceID.InstanceIDDefinition instanceIDDefinition = IDManager.ServiceInstanceID.analysisId(instanceId);
            process.setInstanceName(instanceIDDefinition.getName());
            process.setLayer(Layer.valueOf(resultSet.getInt(ProcessTraffic.LAYER)).name());
            process.setAgentId(resultSet.getString(ProcessTraffic.AGENT_ID));
            process.setDetectType(ProcessDetectType.valueOf(resultSet.getInt(ProcessTraffic.DETECT_TYPE)).name());
            String propertiesString = resultSet.getString(ProcessTraffic.PROPERTIES);
            if (!Strings.isNullOrEmpty(propertiesString)) {
                JsonObject properties = GSON.fromJson(propertiesString, JsonObject.class);
                for (Map.Entry<String, JsonElement> property : properties.entrySet()) {
                    String key = property.getKey();
                    String value = property.getValue().getAsString();
                    process.getAttributes().add(new Attribute(key, value));
                }
            }

            processes.add(process);
        }
        return processes;
    }
}
