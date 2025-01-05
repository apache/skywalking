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

package org.apache.skywalking.oap.server.core.analysis.manual.relation.instance;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.server.core.analysis.MetricsExtension;
import org.apache.skywalking.oap.server.core.analysis.Stream;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.analysis.worker.MetricsStreamProcessor;
import org.apache.skywalking.oap.server.core.remote.grpc.proto.RemoteData;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.core.storage.StorageID;
import org.apache.skywalking.oap.server.core.storage.annotation.BanyanDB;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;
import org.apache.skywalking.oap.server.core.storage.annotation.ElasticSearch;
import org.apache.skywalking.oap.server.core.storage.type.Convert2Entity;
import org.apache.skywalking.oap.server.core.storage.type.Convert2Storage;
import org.apache.skywalking.oap.server.core.storage.type.StorageBuilder;

@Stream(name = ServiceInstanceRelationClientSideMetrics.INDEX_NAME, scopeId = DefaultScopeDefine.SERVICE_INSTANCE_RELATION,
    builder = ServiceInstanceRelationClientSideMetrics.Builder.class, processor = MetricsStreamProcessor.class)
@MetricsExtension(supportDownSampling = true, supportUpdate = false, timeRelativeID = true)
@EqualsAndHashCode(of = {
    "entityId"
}, callSuper = true)
public class ServiceInstanceRelationClientSideMetrics extends Metrics {

    public static final String INDEX_NAME = "service_instance_relation_client_side";
    public static final String SOURCE_SERVICE_ID = "source_service_id";
    public static final String SOURCE_SERVICE_INSTANCE_ID = "source_service_instance_id";
    public static final String DEST_SERVICE_ID = "dest_service_id";
    public static final String DEST_SERVICE_INSTANCE_ID = "dest_service_instance_id";

    @Setter
    @Getter
    @Column(name = SOURCE_SERVICE_ID, length = 250)
    private String sourceServiceId;
    @Setter
    @Getter
    @Column(name = SOURCE_SERVICE_INSTANCE_ID, length = 250)
    private String sourceServiceInstanceId;
    @Setter
    @Getter
    @Column(name = DEST_SERVICE_ID, length = 250)
    private String destServiceId;
    @Setter
    @Getter
    @Column(name = DEST_SERVICE_INSTANCE_ID, length = 250)
    private String destServiceInstanceId;
    @Setter
    @Getter
    @ElasticSearch.EnableDocValues
    @Column(name = ENTITY_ID, length = 512)
    @BanyanDB.SeriesID(index = 0)
    private String entityId;

    @Override
    protected StorageID id0() {
        return new StorageID().append(TIME_BUCKET, getTimeBucket())
                              .append(ENTITY_ID, entityId);
    }

    @Override
    public boolean combine(Metrics metrics) {
        return false;
    }

    @Override
    public void calculate() {

    }

    @Override
    public Metrics toHour() {
        ServiceInstanceRelationClientSideMetrics metrics = new ServiceInstanceRelationClientSideMetrics();
        metrics.setTimeBucket(toTimeBucketInHour());
        metrics.setSourceServiceId(getSourceServiceId());
        metrics.setSourceServiceInstanceId(getSourceServiceInstanceId());
        metrics.setDestServiceId(getDestServiceId());
        metrics.setDestServiceInstanceId(getDestServiceInstanceId());
        metrics.setEntityId(getEntityId());
        return metrics;
    }

    @Override
    public Metrics toDay() {
        ServiceInstanceRelationClientSideMetrics metrics = new ServiceInstanceRelationClientSideMetrics();
        metrics.setTimeBucket(toTimeBucketInDay());
        metrics.setSourceServiceId(getSourceServiceId());
        metrics.setSourceServiceInstanceId(getSourceServiceInstanceId());
        metrics.setDestServiceId(getDestServiceId());
        metrics.setDestServiceInstanceId(getDestServiceInstanceId());
        metrics.setEntityId(getEntityId());
        return metrics;
    }

    @Override
    public int remoteHashCode() {
        int n = 17;
        n = 31 * n + this.entityId.hashCode();
        return n;
    }

    @Override
    public void deserialize(RemoteData remoteData) {
        setEntityId(remoteData.getDataStrings(0));
        setSourceServiceId(remoteData.getDataStrings(1));
        setSourceServiceInstanceId(remoteData.getDataStrings(2));
        setDestServiceId(remoteData.getDataStrings(3));
        setDestServiceInstanceId(remoteData.getDataStrings(4));

        setTimeBucket(remoteData.getDataLongs(0));
    }

    @Override
    public RemoteData.Builder serialize() {
        RemoteData.Builder remoteBuilder = RemoteData.newBuilder();

        remoteBuilder.addDataStrings(getEntityId());
        remoteBuilder.addDataStrings(getSourceServiceId());
        remoteBuilder.addDataStrings(getSourceServiceInstanceId());
        remoteBuilder.addDataStrings(getDestServiceId());
        remoteBuilder.addDataStrings(getDestServiceInstanceId());

        remoteBuilder.addDataLongs(getTimeBucket());
        return remoteBuilder;
    }

    public static class Builder implements StorageBuilder<ServiceInstanceRelationClientSideMetrics> {
        @Override
        public ServiceInstanceRelationClientSideMetrics storage2Entity(final Convert2Entity converter) {
            ServiceInstanceRelationClientSideMetrics metrics = new ServiceInstanceRelationClientSideMetrics();
            metrics.setEntityId((String) converter.get(ENTITY_ID));
            metrics.setSourceServiceId((String) converter.get(SOURCE_SERVICE_ID));
            metrics.setSourceServiceInstanceId((String) converter.get(SOURCE_SERVICE_INSTANCE_ID));
            metrics.setDestServiceId((String) converter.get(DEST_SERVICE_ID));
            metrics.setDestServiceInstanceId((String) converter.get(DEST_SERVICE_INSTANCE_ID));
            metrics.setTimeBucket(((Number) converter.get(TIME_BUCKET)).longValue());
            return metrics;
        }

        @Override
        public void entity2Storage(final ServiceInstanceRelationClientSideMetrics storageData,
                                   final Convert2Storage converter) {
            converter.accept(ENTITY_ID, storageData.getEntityId());
            converter.accept(SOURCE_SERVICE_ID, storageData.getSourceServiceId());
            converter.accept(SOURCE_SERVICE_INSTANCE_ID, storageData.getSourceServiceInstanceId());
            converter.accept(DEST_SERVICE_ID, storageData.getDestServiceId());
            converter.accept(DEST_SERVICE_INSTANCE_ID, storageData.getDestServiceInstanceId());
            converter.accept(TIME_BUCKET, storageData.getTimeBucket());
        }
    }
}
