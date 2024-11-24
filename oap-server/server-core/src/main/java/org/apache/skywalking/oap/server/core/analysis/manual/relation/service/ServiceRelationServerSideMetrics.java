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

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.server.core.analysis.MetricsExtension;
import org.apache.skywalking.oap.server.core.analysis.Stream;
import org.apache.skywalking.oap.server.core.analysis.metrics.IntList;
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

@Stream(name = ServiceRelationServerSideMetrics.INDEX_NAME, scopeId = DefaultScopeDefine.SERVICE_RELATION,
    builder = ServiceRelationServerSideMetrics.Builder.class, processor = MetricsStreamProcessor.class)
@MetricsExtension(supportDownSampling = true, supportUpdate = true, timeRelativeID = true)
@EqualsAndHashCode(of = {
    "entityId"
}, callSuper = true)
@BanyanDB.IndexMode
public class ServiceRelationServerSideMetrics extends Metrics {

    public static final String INDEX_NAME = "service_relation_server_side";
    public static final String SOURCE_SERVICE_ID = "source_service_id";
    public static final String DEST_SERVICE_ID = "dest_service_id";
    public static final String COMPONENT_IDS = "component_ids";

    @Setter
    @Getter
    @Column(name = SOURCE_SERVICE_ID, length = 250)
    private String sourceServiceId;
    @Setter
    @Getter
    @Column(name = DEST_SERVICE_ID, length = 250)
    private String destServiceId;
    @Setter
    @Getter
    @Column(name = COMPONENT_IDS, storageOnly = true)
    @ElasticSearch.Keyword
    @ElasticSearch.EnableDocValues
    private IntList componentIds = new IntList(3);
    @Setter
    @Getter
    @ElasticSearch.EnableDocValues
    @Column(name = ENTITY_ID, length = 512)
    @BanyanDB.SeriesID(index = 0)
    private String entityId;

    @Override
    protected StorageID id0() {
        return new StorageID()
            .append(TIME_BUCKET, getTimeBucket())
            .append(ENTITY_ID, getEntityId());
    }

    @Override
    public boolean combine(Metrics metrics) {
        ServiceRelationServerSideMetrics serviceRelationServerSideMetrics = (ServiceRelationServerSideMetrics) metrics;
        final IntList sourceIDs = this.getComponentIds();
        final IntList targetIDs = serviceRelationServerSideMetrics.getComponentIds();
        boolean changed = false;
        for (int i = 0; i < targetIDs.size(); i++) {
            final int targetID = targetIDs.get(i);
            if (!sourceIDs.include(targetID)) {
                sourceIDs.add(targetID);
                changed = true;
            }
        }
        return changed;
    }

    @Override
    public void calculate() {

    }

    @Override
    public Metrics toHour() {
        ServiceRelationServerSideMetrics metrics = new ServiceRelationServerSideMetrics();
        metrics.setTimeBucket(toTimeBucketInHour());
        metrics.setSourceServiceId(getSourceServiceId());
        metrics.setDestServiceId(getDestServiceId());
        metrics.getComponentIds().copyFrom(getComponentIds());
        metrics.setEntityId(getEntityId());
        return metrics;
    }

    @Override
    public Metrics toDay() {
        ServiceRelationServerSideMetrics metrics = new ServiceRelationServerSideMetrics();
        metrics.setTimeBucket(toTimeBucketInDay());
        metrics.setSourceServiceId(getSourceServiceId());
        metrics.setDestServiceId(getDestServiceId());
        metrics.getComponentIds().copyFrom(getComponentIds());
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
        setDestServiceId(remoteData.getDataStrings(2));
        setComponentIds(new IntList(remoteData.getDataStrings(3)));

        setTimeBucket(remoteData.getDataLongs(0));
    }

    @Override
    public RemoteData.Builder serialize() {
        RemoteData.Builder remoteBuilder = RemoteData.newBuilder();
        remoteBuilder.addDataStrings(getEntityId());
        remoteBuilder.addDataStrings(getSourceServiceId());
        remoteBuilder.addDataStrings(getDestServiceId());
        remoteBuilder.addDataStrings(getComponentIds().toStorageData());

        remoteBuilder.addDataLongs(getTimeBucket());
        return remoteBuilder;
    }

    public static class Builder implements StorageBuilder<ServiceRelationServerSideMetrics> {
        @Override
        public ServiceRelationServerSideMetrics storage2Entity(final Convert2Entity converter) {
            ServiceRelationServerSideMetrics metrics = new ServiceRelationServerSideMetrics();
            metrics.setEntityId((String) converter.get(ENTITY_ID));
            metrics.setSourceServiceId((String) converter.get(SOURCE_SERVICE_ID));
            metrics.setDestServiceId((String) converter.get(DEST_SERVICE_ID));
            metrics.setComponentIds(new IntList((String) converter.get(COMPONENT_IDS)));
            metrics.setTimeBucket(((Number) converter.get(TIME_BUCKET)).longValue());
            return metrics;
        }

        @Override
        public void entity2Storage(final ServiceRelationServerSideMetrics storageData,
                                   final Convert2Storage converter) {
            converter.accept(ENTITY_ID, storageData.getEntityId());
            converter.accept(SOURCE_SERVICE_ID, storageData.getSourceServiceId());
            converter.accept(DEST_SERVICE_ID, storageData.getDestServiceId());
            converter.accept(COMPONENT_IDS, storageData.getComponentIds());
            converter.accept(TIME_BUCKET, storageData.getTimeBucket());
        }
    }
}
