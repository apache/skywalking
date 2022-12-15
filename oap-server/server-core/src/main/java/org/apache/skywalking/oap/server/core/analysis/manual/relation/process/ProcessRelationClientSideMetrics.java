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

package org.apache.skywalking.oap.server.core.analysis.manual.relation.process;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.server.core.analysis.MetricsExtension;
import org.apache.skywalking.oap.server.core.analysis.Stream;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.analysis.worker.MetricsStreamProcessor;
import org.apache.skywalking.oap.server.core.remote.grpc.proto.RemoteData;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.core.storage.ShardingAlgorithm;
import org.apache.skywalking.oap.server.core.storage.StorageID;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;
import org.apache.skywalking.oap.server.core.storage.annotation.SQLDatabase;
import org.apache.skywalking.oap.server.core.storage.type.Convert2Entity;
import org.apache.skywalking.oap.server.core.storage.type.Convert2Storage;
import org.apache.skywalking.oap.server.core.storage.type.StorageBuilder;

@Stream(name = ProcessRelationClientSideMetrics.INDEX_NAME, scopeId = DefaultScopeDefine.PROCESS_RELATION,
    builder = ProcessRelationClientSideMetrics.Builder.class, processor = MetricsStreamProcessor.class)
@MetricsExtension(supportDownSampling = false, supportUpdate = true, timeRelativeID = true)
@EqualsAndHashCode(of = {
    "entityId",
    "component_id"
}, callSuper = true)
@SQLDatabase.Sharding(shardingAlgorithm = ShardingAlgorithm.NO_SHARDING)
public class ProcessRelationClientSideMetrics extends Metrics {

    public static final String INDEX_NAME = "process_relation_client_side";
    public static final String SERVICE_INSTANCE_ID = "service_instance_id";
    public static final String SOURCE_PROCESS_ID = "source_process_id";
    public static final String DEST_PROCESS_ID = "dest_process_id";
    public static final String COMPONENT_ID = "component_id";

    @Setter
    @Getter
    @Column(columnName = SERVICE_INSTANCE_ID)
    private String serviceInstanceId;
    @Setter
    @Getter
    @Column(columnName = SOURCE_PROCESS_ID)
    private String sourceProcessId;
    @Setter
    @Getter
    @Column(columnName = DEST_PROCESS_ID)
    private String destProcessId;
    @Setter
    @Getter
    @Column(columnName = ENTITY_ID, length = 512)
    private String entityId;
    @Setter
    @Getter
    @Column(columnName = COMPONENT_ID, storageOnly = true)
    private int componentId;

    @Override
    protected StorageID id0() {
        return new StorageID()
            .append(TIME_BUCKET, getTimeBucket())
            .append(ENTITY_ID, getEntityId());
    }

    @Override
    public boolean combine(Metrics metrics) {
        final ProcessRelationClientSideMetrics processRelationClientSideMetrics = (ProcessRelationClientSideMetrics) metrics;
        if (!ProcessNetworkRelationIDs.compare(this.componentId, processRelationClientSideMetrics.getComponentId())) {
            this.setComponentId(processRelationClientSideMetrics.getComponentId());
            return true;
        }
        return false;
    }

    @Override
    public void calculate() {
    }

    @Override
    public Metrics toHour() {
        return null;
    }

    @Override
    public Metrics toDay() {
        return null;
    }

    @Override
    public void deserialize(RemoteData remoteData) {
        setServiceInstanceId(remoteData.getDataStrings(0));
        setSourceProcessId(remoteData.getDataStrings(1));
        setDestProcessId(remoteData.getDataStrings(2));
        setEntityId(remoteData.getDataStrings(3));
        setTimeBucket(remoteData.getDataLongs(0));
        setComponentId(remoteData.getDataIntegers(0));
    }

    @Override
    public RemoteData.Builder serialize() {
        final RemoteData.Builder builder = RemoteData.newBuilder();
        builder.addDataStrings(getServiceInstanceId());
        builder.addDataStrings(getSourceProcessId());
        builder.addDataStrings(getDestProcessId());
        builder.addDataStrings(getEntityId());
        builder.addDataLongs(getTimeBucket());
        builder.addDataIntegers(getComponentId());
        return builder;
    }

    @Override
    public int remoteHashCode() {
        return this.entityId.hashCode();
    }

    public static class Builder implements StorageBuilder<ProcessRelationClientSideMetrics> {
        @Override
        public ProcessRelationClientSideMetrics storage2Entity(final Convert2Entity converter) {
            ProcessRelationClientSideMetrics metrics = new ProcessRelationClientSideMetrics();
            metrics.setServiceInstanceId((String) converter.get(SERVICE_INSTANCE_ID));
            metrics.setSourceProcessId((String) converter.get(SOURCE_PROCESS_ID));
            metrics.setDestProcessId((String) converter.get(DEST_PROCESS_ID));
            metrics.setTimeBucket(((Number) converter.get(TIME_BUCKET)).longValue());
            metrics.setEntityId((String) converter.get(ENTITY_ID));
            metrics.setComponentId(((Number) converter.get(COMPONENT_ID)).intValue());
            return metrics;
        }

        @Override
        public void entity2Storage(final ProcessRelationClientSideMetrics storageData,
                                   final Convert2Storage converter) {
            converter.accept(TIME_BUCKET, storageData.getTimeBucket());
            converter.accept(SERVICE_INSTANCE_ID, storageData.getServiceInstanceId());
            converter.accept(SOURCE_PROCESS_ID, storageData.getSourceProcessId());
            converter.accept(DEST_PROCESS_ID, storageData.getDestProcessId());
            converter.accept(ENTITY_ID, storageData.getEntityId());
            converter.accept(COMPONENT_ID, storageData.getComponentId());
        }
    }
}
