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

package org.apache.skywalking.oap.server.core.analysis.manual.relation.service;

import java.util.HashMap;
import java.util.Map;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.analysis.Stream;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.analysis.worker.MetricsStreamProcessor;
import org.apache.skywalking.oap.server.core.remote.grpc.proto.RemoteData;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.core.storage.StorageHashMapBuilder;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;

@Stream(name = ServiceRelationClientSideMetrics.INDEX_NAME, scopeId = DefaultScopeDefine.SERVICE_RELATION,
    builder = ServiceRelationClientSideMetrics.Builder.class, processor = MetricsStreamProcessor.class)
@EqualsAndHashCode(of = {
    "entityId"
}, callSuper = true)
public class ServiceRelationClientSideMetrics extends Metrics {

    public static final String INDEX_NAME = "service_relation_client_side";
    public static final String SOURCE_SERVICE_ID = "source_service_id";
    public static final String DEST_SERVICE_ID = "dest_service_id";
    public static final String COMPONENT_ID = "component_id";

    @Setter
    @Getter
    @Column(columnName = SOURCE_SERVICE_ID)
    private String sourceServiceId;
    @Setter
    @Getter
    @Column(columnName = DEST_SERVICE_ID)
    private String destServiceId;
    @Setter
    @Getter
    @Column(columnName = COMPONENT_ID, storageOnly = true)
    private int componentId;
    @Setter
    @Getter
    @Column(columnName = ENTITY_ID, length = 512)
    private String entityId;

    @Override
    protected String id0() {
        return getTimeBucket() + Const.ID_CONNECTOR + entityId;
    }

    @Override
    public boolean combine(Metrics metrics) {
        return true;
    }

    @Override
    public void calculate() {

    }

    @Override
    public Metrics toHour() {
        ServiceRelationClientSideMetrics metrics = new ServiceRelationClientSideMetrics();
        metrics.setEntityId(getEntityId());
        metrics.setTimeBucket(toTimeBucketInHour());
        metrics.setSourceServiceId(getSourceServiceId());
        metrics.setDestServiceId(getDestServiceId());
        metrics.setComponentId(getComponentId());
        return metrics;
    }

    @Override
    public Metrics toDay() {
        ServiceRelationClientSideMetrics metrics = new ServiceRelationClientSideMetrics();
        metrics.setEntityId(getEntityId());
        metrics.setTimeBucket(toTimeBucketInDay());
        metrics.setSourceServiceId(getSourceServiceId());
        metrics.setDestServiceId(getDestServiceId());
        metrics.setComponentId(getComponentId());
        return metrics;
    }

    @Override
    public int remoteHashCode() {
        return this.hashCode();
    }

    @Override
    public void deserialize(RemoteData remoteData) {
        setEntityId(remoteData.getDataStrings(0));
        setSourceServiceId(remoteData.getDataStrings(1));
        setDestServiceId(remoteData.getDataStrings(2));

        setComponentId(remoteData.getDataIntegers(0));

        setTimeBucket(remoteData.getDataLongs(0));
    }

    @Override
    public RemoteData.Builder serialize() {
        RemoteData.Builder remoteBuilder = RemoteData.newBuilder();
        remoteBuilder.addDataStrings(getEntityId());
        remoteBuilder.addDataStrings(getSourceServiceId());
        remoteBuilder.addDataStrings(getDestServiceId());

        remoteBuilder.addDataIntegers(getComponentId());

        remoteBuilder.addDataLongs(getTimeBucket());
        return remoteBuilder;
    }

    public static class Builder implements StorageHashMapBuilder<ServiceRelationClientSideMetrics> {

        @Override
        public ServiceRelationClientSideMetrics storage2Entity(Map<String, Object> dbMap) {
            ServiceRelationClientSideMetrics metrics = new ServiceRelationClientSideMetrics();
            metrics.setSourceServiceId((String) dbMap.get(SOURCE_SERVICE_ID));
            metrics.setDestServiceId((String) dbMap.get(DEST_SERVICE_ID));
            metrics.setComponentId(((Number) dbMap.get(COMPONENT_ID)).intValue());
            metrics.setTimeBucket(((Number) dbMap.get(TIME_BUCKET)).longValue());
            metrics.setEntityId((String) dbMap.get(ENTITY_ID));
            return metrics;
        }

        @Override
        public Map<String, Object> entity2Storage(ServiceRelationClientSideMetrics storageData) {
            Map<String, Object> map = new HashMap<>();
            map.put(TIME_BUCKET, storageData.getTimeBucket());
            map.put(SOURCE_SERVICE_ID, storageData.getSourceServiceId());
            map.put(DEST_SERVICE_ID, storageData.getDestServiceId());
            map.put(COMPONENT_ID, storageData.getComponentId());
            map.put(ENTITY_ID, storageData.getEntityId());
            return map;
        }
    }
}
