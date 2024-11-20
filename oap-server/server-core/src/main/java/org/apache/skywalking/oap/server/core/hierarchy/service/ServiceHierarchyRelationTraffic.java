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

package org.apache.skywalking.oap.server.core.hierarchy.service;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
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

@Stream(name = ServiceHierarchyRelationTraffic.INDEX_NAME, scopeId = DefaultScopeDefine.SERVICE_HIERARCHY_RELATION,
    builder = ServiceHierarchyRelationTraffic.Builder.class, processor = MetricsStreamProcessor.class)
@MetricsExtension(supportDownSampling = false, supportUpdate = false)
@EqualsAndHashCode(callSuper = false)
@BanyanDB.IndexMode
public class ServiceHierarchyRelationTraffic extends Metrics {
    public static final String INDEX_NAME = "service_hierarchy_relation";
    public static final String SERVICE_ID = "service_id";
    public static final String SERVICE_LAYER = "service_layer";
    public static final String RELATED_SERVICE_ID = "related_service_id";
    public static final String RELATED_SERVICE_LAYER = "related_service_layer";

    /**
     * The service id of the upper service.
     */
    @Setter
    @Getter
    @Column(name = SERVICE_ID, length = 250)
    @BanyanDB.SeriesID(index = 0)
    private String serviceId;

    /**
     * The service layer of the upper service.
     */
    @Setter
    @Getter
    @Column(name = SERVICE_LAYER)
    @BanyanDB.SeriesID(index = 1)
    private Layer serviceLayer = Layer.UNDEFINED;

    /**
     * The service id of the lower service.
     */
    @Setter
    @Getter
    @Column(name = RELATED_SERVICE_ID, length = 250)
    @BanyanDB.SeriesID(index = 2)
    private String relatedServiceId;

    /**
     * The service layer of the lower service.
     */
    @Setter
    @Getter
    @Column(name = RELATED_SERVICE_LAYER)
    @BanyanDB.SeriesID(index = 3)
    private Layer relatedServiceLayer = Layer.UNDEFINED;

    @Override
    protected StorageID id0() {
        String id = IDManager.ServiceID.buildServiceHierarchyRelationId(
            new IDManager.ServiceID.ServiceHierarchyRelationDefine(
                serviceId, serviceLayer, relatedServiceId, relatedServiceLayer));

        return new StorageID().appendMutant(new String[] {
            SERVICE_ID,
            SERVICE_LAYER,
            RELATED_SERVICE_ID,
            RELATED_SERVICE_LAYER
        }, id);
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
        setServiceId(remoteData.getDataStrings(0));
        setServiceLayer(Layer.valueOf(remoteData.getDataIntegers(0)));
        setRelatedServiceId(remoteData.getDataStrings(1));
        setRelatedServiceLayer(Layer.valueOf(remoteData.getDataIntegers(1)));
        setTimeBucket(remoteData.getDataLongs(0));
    }

    @Override
    public RemoteData.Builder serialize() {
        final RemoteData.Builder builder = RemoteData.newBuilder();
        builder.addDataStrings(serviceId);
        builder.addDataIntegers(serviceLayer.value());
        builder.addDataStrings(relatedServiceId);
        builder.addDataIntegers(relatedServiceLayer.value());
        builder.addDataLongs(getTimeBucket());
        return builder;
    }

    @Override
    public int remoteHashCode() {
        return this.hashCode();
    }

    public static class Builder implements StorageBuilder<ServiceHierarchyRelationTraffic> {
        @Override
        public ServiceHierarchyRelationTraffic storage2Entity(final Convert2Entity converter) {
            ServiceHierarchyRelationTraffic traffic = new ServiceHierarchyRelationTraffic();
            traffic.setServiceId((String) converter.get(SERVICE_ID));
            traffic.setRelatedServiceId((String) converter.get(RELATED_SERVICE_ID));
            if (converter.get(SERVICE_LAYER) != null) {
                traffic.setServiceLayer(Layer.valueOf(((Number) converter.get(SERVICE_LAYER)).intValue()));
            } else {
                traffic.setServiceLayer(Layer.UNDEFINED);
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
        public void entity2Storage(final ServiceHierarchyRelationTraffic storageData, final Convert2Storage converter) {
            converter.accept(SERVICE_ID, storageData.getServiceId());
            converter.accept(RELATED_SERVICE_ID, storageData.getRelatedServiceId());
            converter.accept(SERVICE_LAYER, storageData.getServiceLayer().value());
            converter.accept(RELATED_SERVICE_LAYER, storageData.getRelatedServiceLayer().value());
            converter.accept(TIME_BUCKET, storageData.getTimeBucket());
        }
    }
}
