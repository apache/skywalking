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

package org.apache.skywalking.oap.server.storage.plugin.influxdb.query;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.analysis.manual.process.ProcessDetectType;
import org.apache.skywalking.oap.server.core.analysis.manual.process.ProcessTraffic;
import org.apache.skywalking.oap.server.core.query.type.Process;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.manual.endpoint.EndpointTraffic;
import org.apache.skywalking.oap.server.core.analysis.manual.instance.InstanceTraffic;
import org.apache.skywalking.oap.server.core.analysis.manual.service.ServiceTraffic;
import org.apache.skywalking.oap.server.core.query.enumeration.Language;
import org.apache.skywalking.oap.server.core.query.type.Attribute;
import org.apache.skywalking.oap.server.core.query.type.Endpoint;
import org.apache.skywalking.oap.server.core.query.type.Service;
import org.apache.skywalking.oap.server.core.query.type.ServiceInstance;
import org.apache.skywalking.oap.server.core.storage.query.IMetadataQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.influxdb.InfluxClient;
import org.influxdb.dto.Query;
import org.influxdb.dto.QueryResult;
import org.influxdb.querybuilder.SelectQueryImpl;
import org.influxdb.querybuilder.SelectSubQueryImpl;
import org.influxdb.querybuilder.WhereQueryImpl;
import static org.apache.skywalking.oap.server.storage.plugin.influxdb.InfluxConstants.ID_COLUMN;
import static org.apache.skywalking.oap.server.storage.plugin.influxdb.InfluxConstants.NAME;
import static org.apache.skywalking.oap.server.storage.plugin.influxdb.InfluxConstants.TagName;
import static org.influxdb.querybuilder.BuiltQuery.QueryBuilder.contains;
import static org.influxdb.querybuilder.BuiltQuery.QueryBuilder.eq;
import static org.influxdb.querybuilder.BuiltQuery.QueryBuilder.gte;
import static org.influxdb.querybuilder.BuiltQuery.QueryBuilder.select;

@RequiredArgsConstructor
@Slf4j
public class MetadataQuery implements IMetadataQueryDAO {
    private static final Gson GSON = new Gson();
    private final InfluxClient client;

    @Override
    public List<Service> listServices(final String layer, final String group) throws IOException {
        final SelectQueryImpl query = select(
            ServiceTraffic.SERVICE_ID, NAME, ServiceTraffic.GROUP, ServiceTraffic.SHORT_NAME, ServiceTraffic.LAYER)
            .from(client.getDatabase(), ServiceTraffic.INDEX_NAME);
        if (StringUtil.isNotEmpty(layer) && StringUtil.isNotEmpty(group)) {
            query.where(eq(TagName.LAYER, String.valueOf(Layer.valueOf(layer).value())))
                 .and(eq(TagName.SERVICE_GROUP, group));
        } else if (StringUtil.isNotEmpty(layer) && !StringUtil.isNotEmpty(group)) {
            query.where(eq(TagName.LAYER, String.valueOf(Layer.valueOf(layer).value())));
        } else if (!StringUtil.isNotEmpty(layer) && StringUtil.isNotEmpty(group)) {
            query.where(eq(TagName.SERVICE_GROUP, group));
        }

        return buildServices(query);
    }

    @Override
    public List<Service> getServices(final String serviceId) throws IOException {
        final WhereQueryImpl<SelectQueryImpl> where = select(
            ServiceTraffic.SERVICE_ID, NAME, ServiceTraffic.GROUP, ServiceTraffic.SHORT_NAME, ServiceTraffic.LAYER)
            .from(client.getDatabase(), ServiceTraffic.INDEX_NAME)
            .where(eq(TagName.SERVICE_ID, serviceId));
        return buildServices(where);
    }

    @Override
    public List<ServiceInstance> listInstances(final long startTimestamp,
                                               final long endTimestamp,
                                               final String serviceId) throws IOException {
        final long minuteTimeBucket = TimeBucket.getMinuteTimeBucket(startTimestamp);

        SelectSubQueryImpl<SelectQueryImpl> subQuery = select()
            .fromSubQuery(client.getDatabase())
            .column(ID_COLUMN).column(NAME).column(InstanceTraffic.PROPERTIES).column(InstanceTraffic.LAYER)
            .from(InstanceTraffic.INDEX_NAME)
            .where()
            .and(gte(InstanceTraffic.LAST_PING_TIME_BUCKET, minuteTimeBucket))
            .and(eq(TagName.SERVICE_ID, serviceId))
            .groupBy(TagName.NAME);

        SelectQueryImpl query = select().column(ID_COLUMN)
                                        .column(NAME)
                                        .column(InstanceTraffic.PROPERTIES).column(InstanceTraffic.LAYER)
                                        .from(client.getDatabase(), InstanceTraffic.INDEX_NAME);
        query.setSubQuery(subQuery);
        return buildInstances(query);
    }

    @Override
    public ServiceInstance getInstance(final String instanceId) throws IOException {
        final WhereQueryImpl<SelectQueryImpl> where = select(
            ID_COLUMN, NAME, InstanceTraffic.LAYER)
            .from(client.getDatabase(), InstanceTraffic.INDEX_NAME)
            .where(eq(TagName.ID_COLUMN, instanceId));
        final List<ServiceInstance> instances = buildInstances(where);
        return instances.size() > 0 ? instances.get(0) : null;
    }

    @Override
    public List<Endpoint> findEndpoint(final String keyword,
                                       final String serviceId,
                                       final int limit) throws IOException {
        final WhereQueryImpl<SelectQueryImpl> where = select()
            .column(ID_COLUMN)
            .column(NAME)
            .from(client.getDatabase(), EndpointTraffic.INDEX_NAME)
            .where(eq(TagName.SERVICE_ID, String.valueOf(serviceId)));
        if (!Strings.isNullOrEmpty(keyword)) {
            where.and(contains(NAME, keyword.replaceAll("/", "\\\\/")));
        }
        where.limit(limit);

        final QueryResult.Series series = client.queryForSingleSeries(where);
        if (log.isDebugEnabled()) {
            log.debug("SQL: {} result: {}.", where.getCommand(), series);
        }

        List<Endpoint> list = new ArrayList<>(limit);
        if (series != null) {
            series.getValues().forEach(values -> {
                Endpoint endpoint = new Endpoint();
                endpoint.setId((String) values.get(1));
                endpoint.setName((String) values.get(2));
                list.add(endpoint);
            });
        }
        return list;
    }

    @Override
    public List<Process> listProcesses(String serviceId, String instanceId, String agentId) throws IOException {
        final SelectQueryImpl query = select(
                ID_COLUMN, ProcessTraffic.NAME, ProcessTraffic.SERVICE_ID, ProcessTraffic.INSTANCE_ID,
                ProcessTraffic.LAYER, ProcessTraffic.AGENT_ID, ProcessTraffic.DETECT_TYPE, ProcessTraffic.PROPERTIES)
                .from(client.getDatabase(), ProcessTraffic.INDEX_NAME);
        final WhereQueryImpl<SelectQueryImpl> whereQuery = query.where();
        if (StringUtil.isNotEmpty(serviceId)) {
            whereQuery.and(eq(TagName.SERVICE_ID, serviceId));
        }
        if (StringUtil.isNotEmpty(instanceId)) {
            whereQuery.and(eq(TagName.INSTANCE_ID, instanceId));
        }
        if (StringUtil.isNotEmpty(agentId)) {
            whereQuery.and(eq(TagName.AGENT_ID, agentId));
        }

        return buildProcesses(query);
    }

    @Override
    public Process getProcess(String processId) throws IOException {
        final WhereQueryImpl<SelectQueryImpl> where = select(
                ID_COLUMN, ProcessTraffic.NAME, ProcessTraffic.SERVICE_ID, ProcessTraffic.INSTANCE_ID,
                ProcessTraffic.LAYER, ProcessTraffic.AGENT_ID, ProcessTraffic.DETECT_TYPE, ProcessTraffic.PROPERTIES)
                .from(client.getDatabase(), ProcessTraffic.INDEX_NAME)
                .where(eq(TagName.ID_COLUMN, processId));
        final List<Process> processes = buildProcesses(where);
        return processes.size() > 0 ? processes.get(0) : null;
    }

    private List<Service> buildServices(Query query) throws IOException {
        QueryResult.Series series = client.queryForSingleSeries(query);
        if (log.isDebugEnabled()) {
            log.debug("SQL: {} result: {}", query.getCommand(), series);
        }

        if (Objects.isNull(series)) {
            return Collections.emptyList();
        }

        List<Service> services = new ArrayList<>();
        if (Objects.nonNull(series)) {
            for (List<Object> values : series.getValues()) {
                String serviceName = (String) values.get(1);
                Service service = new Service();
                service.setId((String) values.get(1));
                service.setName((String) values.get(2));
                service.setGroup((String) values.get(3));
                service.setShortName((String) values.get(4));
                service.getLayers().add(Layer.valueOf((int) values.get(5)).name());
                services.add(service);
            }
        }
        return services;
    }

    private List<ServiceInstance> buildInstances(Query query) throws IOException {
        QueryResult.Series series = client.queryForSingleSeries(query);
        if (log.isDebugEnabled()) {
            log.debug("SQL: {} result: {}", query.getCommand(), series);
        }
        if (Objects.isNull(series)) {
            return Collections.emptyList();
        }

        List<List<Object>> result = series.getValues();
        List<ServiceInstance> instances = Lists.newArrayList();
        for (List<Object> values : result) {
            ServiceInstance serviceInstance = new ServiceInstance();

            serviceInstance.setId((String) values.get(1));
            serviceInstance.setName((String) values.get(2));
            serviceInstance.setInstanceUUID(serviceInstance.getId());

            String propertiesString = (String) values.get(3);
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
            serviceInstance.setLayer(Layer.valueOf((int) values.get(4)).name());
            instances.add(serviceInstance);
        }
        return instances;
    }

    private List<Process> buildProcesses(Query query) throws IOException {
        QueryResult.Series series = client.queryForSingleSeries(query);
        if (log.isDebugEnabled()) {
            log.debug("SQL: {} result: {}", query.getCommand(), series);
        }
        if (Objects.isNull(series)) {
            return Collections.emptyList();
        }

        List<List<Object>> result = series.getValues();
        List<Process> instances = Lists.newArrayList();
        for (List<Object> values : result) {
            Process process = new Process();

            process.setId((String) values.get(1));
            process.setName((String) values.get(2));
            String serviceId = (String) values.get(3);
            process.setServiceId(serviceId);
            process.setServiceName(IDManager.ServiceID.analysisId(serviceId).getName());
            String instanceId = (String) values.get(4);
            process.setInstanceId(instanceId);
            process.setInstanceName(IDManager.ServiceInstanceID.analysisId(instanceId).getName());
            process.setLayer(Layer.valueOf((Integer) values.get(5)).name());
            process.setAgentId((String) values.get(6));
            process.setDetectType(ProcessDetectType.valueOf((Integer) values.get(7)).name());

            String propertiesString = (String) values.get(8);
            if (!Strings.isNullOrEmpty(propertiesString)) {
                JsonObject properties = GSON.fromJson(propertiesString, JsonObject.class);
                for (Map.Entry<String, JsonElement> property : properties.entrySet()) {
                    String key = property.getKey();
                    String value = property.getValue().getAsString();
                    process.getAttributes().add(new Attribute(key, value));
                }
            }
            instances.add(process);
        }
        return instances;
    }
}
