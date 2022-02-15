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

package org.apache.skywalking.oap.server.storage.plugin.banyandb.stream;

import com.google.common.collect.ImmutableList;
import com.google.gson.JsonElement;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import org.apache.skywalking.banyandb.v1.client.RowEntity;
import org.apache.skywalking.banyandb.v1.client.StreamQuery;
import org.apache.skywalking.banyandb.v1.client.StreamQueryResponse;
import org.apache.skywalking.banyandb.v1.client.TagAndValue;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.manual.endpoint.EndpointTraffic;
import org.apache.skywalking.oap.server.core.analysis.manual.instance.InstanceTraffic;
import org.apache.skywalking.oap.server.core.analysis.manual.service.ServiceTraffic;
import org.apache.skywalking.oap.server.core.query.enumeration.Language;
import org.apache.skywalking.oap.server.core.query.type.Attribute;
import org.apache.skywalking.oap.server.core.query.type.Endpoint;
import org.apache.skywalking.oap.server.core.query.type.Service;
import org.apache.skywalking.oap.server.core.query.type.ServiceInstance;
import org.apache.skywalking.oap.server.core.remote.grpc.proto.RemoteData;
import org.apache.skywalking.oap.server.core.storage.query.IMetadataQueryDAO;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBStorageClient;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.StreamMetaInfo;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * {@link org.apache.skywalking.oap.server.core.analysis.manual.service.ServiceTraffic},
 * {@link org.apache.skywalking.oap.server.core.analysis.manual.endpoint.EndpointTraffic}
 * {@link org.apache.skywalking.oap.server.core.analysis.manual.instance.InstanceTraffic}
 * are all streams.
 */
public class BanyanDBMetadataQueryDAO extends AbstractBanyanDBDAO implements IMetadataQueryDAO {
    public BanyanDBMetadataQueryDAO(BanyanDBStorageClient client) {
        super(client);
    }

    @Override
    public List<Service> listServices(final String layer, final String group) throws IOException {
        StreamQueryResponse resp = query(ServiceTraffic.INDEX_NAME,
                ImmutableList.of(ServiceTraffic.NAME, ServiceTraffic.SERVICE_ID, ServiceTraffic.GROUP, ServiceTraffic.LAYER),
                new QueryBuilder() {
                    @Override
                    public void apply(StreamQuery query) {
                        query.setDataProjections(Collections.singletonList(ServiceTraffic.SHORT_NAME));
                        if (StringUtil.isNotEmpty(layer)) {
                            query.appendCondition(eq(ServiceTraffic.LAYER, Layer.valueOf(layer).value()));
                        }
                        if (StringUtil.isNotEmpty(group)) {
                            query.appendCondition(eq(ServiceTraffic.GROUP, group));
                        }
                    }
                });

        return resp.getElements().stream().map(new ServiceDeserializer()).collect(Collectors.toList());
    }

    @Override
    public List<Service> getServices(String serviceId) throws IOException {
        StreamQueryResponse resp = query(ServiceTraffic.INDEX_NAME,
                ImmutableList.of(ServiceTraffic.NAME, ServiceTraffic.SERVICE_ID, ServiceTraffic.GROUP, ServiceTraffic.LAYER),
                new QueryBuilder() {
                    @Override
                    public void apply(StreamQuery query) {
                        query.setDataProjections(Collections.singletonList(ServiceTraffic.SHORT_NAME));

                        query.appendCondition(eq(ServiceTraffic.SERVICE_ID, serviceId));
                    }
                });

        return resp.getElements().stream().map(new ServiceDeserializer()).collect(Collectors.toList());
    }

    @Override
    public List<ServiceInstance> listInstances(long startTimestamp, long endTimestamp, String serviceId) throws IOException {
        StreamQueryResponse resp = query(InstanceTraffic.INDEX_NAME,
                ImmutableList.of(InstanceTraffic.SERVICE_ID, InstanceTraffic.LAST_PING_TIME_BUCKET),
                new QueryBuilder() {
                    @Override
                    public void apply(StreamQuery query) {
                        query.setDataProjections(Collections.singletonList("data_binary"));

                        final long startMinuteTimeBucket = TimeBucket.getMinuteTimeBucket(startTimestamp);
                        final long endMinuteTimeBucket = TimeBucket.getMinuteTimeBucket(endTimestamp);

                        query.appendCondition(gte(InstanceTraffic.LAST_PING_TIME_BUCKET, startMinuteTimeBucket));
                        query.appendCondition(lte(InstanceTraffic.LAST_PING_TIME_BUCKET, endMinuteTimeBucket));
                        query.appendCondition(eq(InstanceTraffic.SERVICE_ID, serviceId));
                    }
                });

        return resp.getElements().stream().map(new ServiceInstanceDeserializer()).collect(Collectors.toList());
    }

    @Override
    public ServiceInstance getInstance(String instanceId) throws IOException {
        StreamQueryResponse resp = query(InstanceTraffic.INDEX_NAME,
                ImmutableList.of(StreamMetaInfo.ID), new QueryBuilder() {
                    @Override
                    public void apply(StreamQuery query) {
                        query.setDataProjections(Collections.singletonList("data_binary"));

                        query.appendCondition(eq(StreamMetaInfo.ID, instanceId));
                    }
                });

        return resp.getElements().stream().map(new ServiceInstanceDeserializer()).findFirst().orElse(null);
    }

    @Override
    public List<Endpoint> findEndpoint(String keyword, String serviceId, int limit) throws IOException {
        StreamQueryResponse resp = query(EndpointTraffic.INDEX_NAME,
                ImmutableList.of(EndpointTraffic.NAME, EndpointTraffic.SERVICE_ID), new QueryBuilder() {
                    @Override
                    public void apply(StreamQuery query) {
                        query.appendCondition(eq(EndpointTraffic.SERVICE_ID, serviceId));
                    }
                });

        return resp.getElements().stream().map(new EndpointDeserializer()).filter(e -> e.getName().contains(keyword))
                .limit(limit).collect(Collectors.toList());
    }

    public static class EndpointDeserializer implements RowEntityDeserializer<Endpoint> {
        @Override
        public Endpoint apply(RowEntity row) {
            Endpoint endpoint = new Endpoint();
            final List<TagAndValue<?>> searchable = row.getTagFamilies().get(0);
            endpoint.setId(row.getId());
            endpoint.setName((String) searchable.get(0).getValue()); // 0 - name
            return endpoint;
        }
    }

    public static class ServiceDeserializer implements RowEntityDeserializer<Service> {
        @Override
        public Service apply(RowEntity row) {
            Service service = new Service();
            final List<TagAndValue<?>> searchable = row.getTagFamilies().get(0);
            service.setName((String) searchable.get(0).getValue());
            service.setId((String) searchable.get(1).getValue());
            service.setGroup((String) searchable.get(2).getValue());
            String layerName = Layer.valueOf(((Number) searchable.get(3).getValue()).intValue()).name();
            service.getLayers().add(layerName);
            final List<TagAndValue<?>> data = row.getTagFamilies().get(1);
            service.setShortName((String) data.get(0).getValue());
            return service;
        }
    }

    public static class ServiceInstanceDeserializer implements RowEntityDeserializer<ServiceInstance> {
        @Override
        public ServiceInstance apply(RowEntity row) {
            InstanceTraffic instanceTraffic = new InstanceTraffic();
            final List<TagAndValue<?>> data = row.getTagFamilies().get(1);
            Object o = data.get(0).getValue();
            ServiceInstance serviceInstance = new ServiceInstance();
            if (o instanceof ByteString && !((ByteString) o).isEmpty()) {
                try {
                    RemoteData remoteData = RemoteData.parseFrom((ByteString) o);
                    instanceTraffic.deserialize(remoteData);
                    serviceInstance.setName(instanceTraffic.getName());
                    serviceInstance.setId(row.getId());
                    serviceInstance.setInstanceUUID(serviceInstance.getId());
                    serviceInstance.setLayer(instanceTraffic.getLayer().name());

                    if (instanceTraffic.getProperties() != null) {
                        for (Map.Entry<String, JsonElement> property : instanceTraffic.getProperties().entrySet()) {
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
                } catch (InvalidProtocolBufferException ex) {
                    throw new RuntimeException("fail to parse remote data", ex);
                }
            } else {
                throw new RuntimeException("unable to parse binary data");
            }

            return serviceInstance;
        }
    }
}
