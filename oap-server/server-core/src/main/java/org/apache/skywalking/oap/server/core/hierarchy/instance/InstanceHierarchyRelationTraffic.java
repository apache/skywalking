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

package org.apache.skywalking.oap.server.core.hierarchy.instance;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.analysis.MetricsExtension;
import org.apache.skywalking.oap.server.core.analysis.Stream;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.analysis.worker.MetricsStreamProcessor;
import org.apache.skywalking.oap.server.core.remote.grpc.proto.RemoteData;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.core.storage.StorageID;
import org.apache.skywalking.oap.server.core.storage.annotation.BanyanDB;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;
import org.apache.skywalking.oap.server.core.storage.type.Convert2Entity;
import org.apache.skywalking.oap.server.core.storage.type.Convert2Storage;
import org.apache.skywalking.oap.server.core.storage.type.StorageBuilder;

@Stream(name = InstanceHierarchyRelationTraffic.INDEX_NAME, scopeId = DefaultScopeDefine.INSTANCE_HIERARCHY_RELATION,
    builder = InstanceHierarchyRelationTraffic.Builder.class, processor = MetricsStreamProcessor.class)
@MetricsExtension(supportDownSampling = false, supportUpdate = false)
@EqualsAndHashCode(of = {
    "entityId"
}, callSuper = true)
public class InstanceHierarchyRelationTraffic extends Metrics {
    public static final String INDEX_NAME = "instance_hierarchy_relation";
    public static final String SERVICE_LAYER = "service_layer";
    public static final String INSTANCE_ID = "instance_id";
    public static final String RELATED_SERVICE_LAYER = "related_service_layer";
    public static final String RELATED_INSTANCE_ID = "related_instance_id";

    @Setter
    @Getter
    @Column(name = ENTITY_ID, length = 512)
    @BanyanDB.SeriesID(index = 0)
    private String entityId;

    @Setter


    @Getter
    @Column(name = INSTANCE_ID, length = 250)
    private String instanceId;

    @Setter
    @Getter
    @Column(name = SERVICE_LAYER)
    private Layer servicelayer = Layer.UNDEFINED;

    @Setter
    @Getter
    @Column(name = RELATED_INSTANCE_ID, length = 250)
    private String relatedInstanceId;

    @Setter
    @Getter
    @Column(name = RELATED_SERVICE_LAYER)
    private Layer relatedServiceLayer = Layer.UNDEFINED;

    @Override
    protected StorageID id0() {
        return new StorageID().append(ENTITY_ID, entityId);
    }

    @Override
    public boolean combine(final Metrics metrics) {
        return true;
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
    public void deserialize(final RemoteData remoteData) {
        setEntityId(remoteData.getDataStrings(0));
        setInstanceId(remoteData.getDataStrings(1));
        setServicelayer(Layer.valueOf(remoteData.getDataIntegers(0)));
        setRelatedInstanceId(remoteData.getDataStrings(2));
        setRelatedServiceLayer(Layer.valueOf(remoteData.getDataIntegers(1)));
        setTimeBucket(remoteData.getDataLongs(0));
    }

    @Override
    public RemoteData.Builder serialize() {
        final RemoteData.Builder builder = RemoteData.newBuilder();
        builder.addDataStrings(getEntityId());
        builder.addDataStrings(instanceId);
        builder.addDataIntegers(servicelayer.value());
        builder.addDataStrings(relatedInstanceId);
        builder.addDataIntegers(relatedServiceLayer.value());
        builder.addDataLongs(getTimeBucket());
        return builder;
    }

    @Override
    public int remoteHashCode() {
        return this.hashCode();
    }

    public static class Builder implements StorageBuilder<InstanceHierarchyRelationTraffic> {
        @Override
        public InstanceHierarchyRelationTraffic storage2Entity(final Convert2Entity converter) {
            InstanceHierarchyRelationTraffic traffic = new InstanceHierarchyRelationTraffic();
            traffic.setInstanceId((String) converter.get(INSTANCE_ID));
            traffic.setRelatedInstanceId((String) converter.get(RELATED_INSTANCE_ID));
            traffic.setEntityId((String) converter.get(ENTITY_ID));
            if (converter.get(SERVICE_LAYER) != null) {
                traffic.setServicelayer(Layer.valueOf(((Number) converter.get(SERVICE_LAYER)).intValue()));
            } else {
                traffic.setServicelayer(Layer.UNDEFINED);
            }
            if (converter.get(RELATED_SERVICE_LAYER) != null) {
                traffic.setRelatedServiceLayer(
                    Layer.valueOf(((Number) converter.get(RELATED_SERVICE_LAYER)).intValue()));
            } else {
                traffic.setRelatedServiceLayer(Layer.UNDEFINED);
            }
            if (converter.get(TIME_BUCKET) != null) {
                traffic.setTimeBucket(((Number) converter.get(TIME_BUCKET)).longValue());
            }
            return traffic;
        }

        @Override
        public void entity2Storage(final InstanceHierarchyRelationTraffic storageData, final Convert2Storage converter) {
            converter.accept(INSTANCE_ID, storageData.getInstanceId());
            converter.accept(RELATED_INSTANCE_ID, storageData.getRelatedInstanceId());
            converter.accept(SERVICE_LAYER, storageData.getServicelayer().value());
            converter.accept(RELATED_SERVICE_LAYER, storageData.getRelatedServiceLayer().value());
            converter.accept(TIME_BUCKET, storageData.getTimeBucket());
            converter.accept(ENTITY_ID, storageData.getEntityId());
        }
    }
}
