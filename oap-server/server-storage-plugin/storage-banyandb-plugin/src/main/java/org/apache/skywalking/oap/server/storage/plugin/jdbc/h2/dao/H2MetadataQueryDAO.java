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
import org.apache.skywalking.apm.util.StringUtil;
import org.apache.skywalking.oap.server.core.analysis.NodeType;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.manual.endpoint.EndpointTraffic;
import org.apache.skywalking.oap.server.core.analysis.manual.instance.InstanceTraffic;
import org.apache.skywalking.oap.server.core.analysis.manual.service.ServiceTraffic;
import org.apache.skywalking.oap.server.core.query.enumeration.Language;
import org.apache.skywalking.oap.server.core.query.type.Attribute;
import org.apache.skywalking.oap.server.core.query.type.Database;
import org.apache.skywalking.oap.server.core.query.type.Endpoint;
import org.apache.skywalking.oap.server.core.query.type.Service;
import org.apache.skywalking.oap.server.core.query.type.ServiceInstance;
import org.apache.skywalking.oap.server.core.storage.query.IMetadataQueryDAO;
import org.apache.skywalking.oap.server.library.client.jdbc.hikaricp.JDBCHikariCPClient;

public class H2MetadataQueryDAO implements IMetadataQueryDAO {
    private static final Gson GSON = new Gson();

    private JDBCHikariCPClient h2Client;
    private int metadataQueryMaxSize;

    public H2MetadataQueryDAO(JDBCHikariCPClient h2Client, int metadataQueryMaxSize) {
        this.h2Client = h2Client;
        this.metadataQueryMaxSize = metadataQueryMaxSize;
    }

    @Override
    public List<Service> getAllServices(final String group) throws IOException {
        StringBuilder sql = new StringBuilder();
        List<Object> condition = new ArrayList<>(5);
        sql.append("select * from ").append(ServiceTraffic.INDEX_NAME).append(" where ");
        sql.append(ServiceTraffic.NODE_TYPE).append("=?");
        condition.add(NodeType.Normal.value());
        if (StringUtil.isNotEmpty(group)) {
            sql.append(" and ").append(ServiceTraffic.GROUP).append("=?");
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
    public List<Service> getAllBrowserServices() throws IOException {
        StringBuilder sql = new StringBuilder();
        List<Object> condition = new ArrayList<>(5);
        sql.append("select * from ").append(ServiceTraffic.INDEX_NAME).append(" where ");
        sql.append(ServiceTraffic.NODE_TYPE).append("=?");
        condition.add(NodeType.Browser.value());
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
    public List<Database> getAllDatabases() throws IOException {
        StringBuilder sql = new StringBuilder();
        List<Object> condition = new ArrayList<>(1);
        sql.append("select * from ").append(ServiceTraffic.INDEX_NAME).append(" where ");
        sql.append(ServiceTraffic.NODE_TYPE).append("=? limit ").append(metadataQueryMaxSize);
        condition.add(NodeType.Database.value());

        try (Connection connection = h2Client.getConnection()) {
            try (ResultSet resultSet = h2Client.executeQuery(
                connection, sql.toString(), condition.toArray(new Object[0]))) {
                List<Database> databases = new ArrayList<>();
                while (resultSet.next()) {
                    Database database = new Database();
                    database.setId(resultSet.getString(H2TableInstaller.ID_COLUMN));
                    database.setName(resultSet.getString(ServiceTraffic.NAME));
                    databases.add(database);
                }
                return databases;
            }
        } catch (SQLException e) {
            throw new IOException(e);
        }
    }

    @Override
    public List<Service> searchServices(String keyword) throws IOException {
        StringBuilder sql = new StringBuilder();
        List<Object> condition = new ArrayList<>(5);
        sql.append("select * from ").append(ServiceTraffic.INDEX_NAME).append(" where ");
        sql.append(ServiceTraffic.NODE_TYPE).append("=?");
        condition.add(NodeType.Normal.value());
        if (!Strings.isNullOrEmpty(keyword)) {
            sql.append(" and ").append(ServiceTraffic.NAME).append(" like concat('%',?,'%')");
            condition.add(keyword);
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
    public Service searchService(String serviceCode) throws IOException {
        StringBuilder sql = new StringBuilder();
        List<Object> condition = new ArrayList<>(5);
        sql.append("select * from ").append(ServiceTraffic.INDEX_NAME).append(" where ");
        sql.append(ServiceTraffic.NODE_TYPE).append("=?");
        condition.add(NodeType.Normal.value());
        sql.append(" and ").append(ServiceTraffic.NAME).append(" = ?");
        condition.add(serviceCode);

        try (Connection connection = h2Client.getConnection()) {
            try (ResultSet resultSet = h2Client.executeQuery(
                connection, sql.toString(), condition.toArray(new Object[0]))) {

                while (resultSet.next()) {
                    Service service = new Service();
                    service.setId(resultSet.getString(H2TableInstaller.ID_COLUMN));
                    service.setName(resultSet.getString(ServiceTraffic.NAME));
                    return service;
                }
            }
        } catch (SQLException e) {
            throw new IOException(e);
        }

        return null;
    }

    @Override
    public List<Endpoint> searchEndpoint(String keyword, String serviceId, int limit) throws IOException {
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

    @Override
    public List<ServiceInstance> getServiceInstances(long startTimestamp, long endTimestamp,
                                                     String serviceId) throws IOException {
        final long minuteTimeBucket = TimeBucket.getMinuteTimeBucket(startTimestamp);

        StringBuilder sql = new StringBuilder();
        List<Object> condition = new ArrayList<>(5);
        sql.append("select * from ").append(InstanceTraffic.INDEX_NAME).append(" where ");
        sql.append(InstanceTraffic.LAST_PING_TIME_BUCKET).append(" >= ?");
        condition.add(minuteTimeBucket);
        sql.append(" and ").append(InstanceTraffic.SERVICE_ID).append("=?");
        condition.add(serviceId);

        List<ServiceInstance> serviceInstances = new ArrayList<>();
        try (Connection connection = h2Client.getConnection()) {
            try (ResultSet resultSet = h2Client.executeQuery(
                connection, sql.toString(), condition.toArray(new Object[0]))) {

                while (resultSet.next()) {
                    ServiceInstance serviceInstance = new ServiceInstance();
                    serviceInstance.setId(resultSet.getString(H2TableInstaller.ID_COLUMN));
                    serviceInstance.setName(resultSet.getString(InstanceTraffic.NAME));
                    serviceInstance.setInstanceUUID(serviceInstance.getId());

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
            }
        } catch (SQLException e) {
            throw new IOException(e);
        }
        return serviceInstances;
    }

    private List<Service> buildServices(ResultSet resultSet) throws SQLException {
        List<Service> services = new ArrayList<>();
        while (resultSet.next()) {
            Service service = new Service();
            service.setId(resultSet.getString(H2TableInstaller.ID_COLUMN));
            service.setName(resultSet.getString(ServiceTraffic.NAME));
            service.setGroup(resultSet.getString(ServiceTraffic.GROUP));
            services.add(service);
        }

        return services;
    }
}
