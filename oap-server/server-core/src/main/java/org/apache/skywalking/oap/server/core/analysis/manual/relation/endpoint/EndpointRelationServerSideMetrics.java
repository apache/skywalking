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

package org.apache.skywalking.oap.server.core.analysis.manual.relation.endpoint;

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

@Stream(name = EndpointRelationServerSideMetrics.INDEX_NAME, scopeId = DefaultScopeDefine.ENDPOINT_RELATION,
    builder = EndpointRelationServerSideMetrics.Builder.class, processor = MetricsStreamProcessor.class)
@MetricsExtension(supportDownSampling = true, supportUpdate = false, timeRelativeID = true)
@EqualsAndHashCode(of = {
    "entityId"
}, callSuper = true)
public class EndpointRelationServerSideMetrics extends Metrics {

    public static final String INDEX_NAME = "endpoint_relation_server_side";
    public static final String SOURCE_ENDPOINT = "source_endpoint";
    public static final String DEST_ENDPOINT = "dest_endpoint";
    public static final String COMPONENT_ID = "component_id";

    @Setter
    @Getter
    @Column(name = SOURCE_ENDPOINT, length = 250)
    private String sourceEndpoint;
    @Setter
    @Getter
    @Column(name = DEST_ENDPOINT, length = 250)
    private String destEndpoint;
    @Setter
    @Getter
    @ElasticSearch.EnableDocValues
    @Column(name = COMPONENT_ID, storageOnly = true)
    @BanyanDB.MeasureField
    private int componentId;
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
        return true;
    }

    @Override
    public void calculate() {

    }

    @Override
    public Metrics toHour() {
        EndpointRelationServerSideMetrics metrics = new EndpointRelationServerSideMetrics();
        metrics.setTimeBucket(toTimeBucketInHour());
        metrics.setSourceEndpoint(getSourceEndpoint());
        metrics.setDestEndpoint(getDestEndpoint());
        metrics.setComponentId(getComponentId());
        metrics.setEntityId(getEntityId());
        return metrics;
    }

    @Override
    public Metrics toDay() {
        EndpointRelationServerSideMetrics metrics = new EndpointRelationServerSideMetrics();
        metrics.setTimeBucket(toTimeBucketInDay());
        metrics.setSourceEndpoint(getSourceEndpoint());
        metrics.setDestEndpoint(getDestEndpoint());
        metrics.setComponentId(getComponentId());
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
        setComponentId(remoteData.getDataIntegers(0));

        setTimeBucket(remoteData.getDataLongs(0));

        setEntityId(remoteData.getDataStrings(0));
        setSourceEndpoint(remoteData.getDataStrings(1));
        setDestEndpoint(remoteData.getDataStrings(2));
    }

    @Override
    public RemoteData.Builder serialize() {
        RemoteData.Builder remoteBuilder = RemoteData.newBuilder();

        remoteBuilder.addDataIntegers(getComponentId());

        remoteBuilder.addDataLongs(getTimeBucket());

        remoteBuilder.addDataStrings(getEntityId());
        remoteBuilder.addDataStrings(getSourceEndpoint());
        remoteBuilder.addDataStrings(getDestEndpoint());
        return remoteBuilder;
    }

    public static class Builder implements StorageBuilder<EndpointRelationServerSideMetrics> {
        @Override
        public EndpointRelationServerSideMetrics storage2Entity(final Convert2Entity converter) {
            EndpointRelationServerSideMetrics metrics = new EndpointRelationServerSideMetrics();
            metrics.setSourceEndpoint((String) converter.get(SOURCE_ENDPOINT));
            metrics.setDestEndpoint((String) converter.get(DEST_ENDPOINT));
            metrics.setComponentId(((Number) converter.get(COMPONENT_ID)).intValue());
            metrics.setTimeBucket(((Number) converter.get(TIME_BUCKET)).longValue());
            metrics.setEntityId((String) converter.get(ENTITY_ID));
            return metrics;
        }

        @Override
        public void entity2Storage(final EndpointRelationServerSideMetrics storageData,
                                   final Convert2Storage converter) {
            converter.accept(SOURCE_ENDPOINT, storageData.getSourceEndpoint());
            converter.accept(DEST_ENDPOINT, storageData.getDestEndpoint());
            converter.accept(COMPONENT_ID, storageData.getComponentId());
            converter.accept(TIME_BUCKET, storageData.getTimeBucket());
            converter.accept(ENTITY_ID, storageData.getEntityId());
        }
    }
}
