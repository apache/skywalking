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

package org.apache.skywalking.oap.server.core.otlp;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.server.core.Const;
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
import org.apache.skywalking.oap.server.library.util.StringUtil;

/**
 * The service-name catalog of natively stored OTLP spans, one row per {@code resource.service.name} and the
 * {@code resource.service.layer} it arrived with, {@code UNDEFINED} when the resource carries no registered layer. Feeds the TraceQL
 * {@code resource.service.name} value list, which a {@code resource.service.layer} condition narrows. The layer is
 * part of the row, not an attribute of the name: rows are insert-only, so a service that later starts setting a
 * layer gains a second row instead of keeping the first one's for good.
 */
@Stream(name = OTLPServiceTraffic.INDEX_NAME, scopeId = DefaultScopeDefine.OTLP_SERVICE,
    builder = OTLPServiceTraffic.Builder.class, processor = MetricsStreamProcessor.class)
@MetricsExtension(supportDownSampling = false, supportUpdate = false)
@EqualsAndHashCode(of = {
    "serviceName",
    "layer"
})
@BanyanDB.IndexMode
public class OTLPServiceTraffic extends Metrics {
    public static final String INDEX_NAME = "otlp_service_traffic";
    public static final String SERVICE_NAME = "service_name";
    /**
     * Neither {@code layer} nor {@code service_layer}: the metrics share one Elasticsearch index unless logic
     * sharding is on, where {@code service_traffic} and the hierarchy relations already hold those columns as
     * {@link Layer} values, not strings.
     */
    public static final String LAYER = "otlp_service_layer";

    @Setter
    @Getter
    @Column(name = SERVICE_NAME)
    private String serviceName = Const.EMPTY_STRING;

    @Setter
    @Getter
    @Column(name = LAYER)
    private String layer = Layer.UNDEFINED.name();

    @Override
    protected StorageID id0() {
        return new StorageID().append(SERVICE_NAME, serviceName).append(LAYER, layer);
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
    public int remoteHashCode() {
        return hashCode();
    }

    @Override
    public void deserialize(final RemoteData remoteData) {
        setServiceName(remoteData.getDataStrings(0));
        setLayer(remoteData.getDataStrings(1));
        setTimeBucket(remoteData.getDataLongs(0));
    }

    @Override
    public RemoteData.Builder serialize() {
        final RemoteData.Builder remoteBuilder = RemoteData.newBuilder();
        remoteBuilder.addDataStrings(getServiceName());
        remoteBuilder.addDataStrings(getLayer());
        remoteBuilder.addDataLongs(getTimeBucket());
        return remoteBuilder;
    }

    public static class Builder implements StorageBuilder<OTLPServiceTraffic> {
        @Override
        public OTLPServiceTraffic storage2Entity(final Convert2Entity converter) {
            final OTLPServiceTraffic traffic = new OTLPServiceTraffic();
            traffic.setServiceName((String) converter.get(SERVICE_NAME));
            // Every row is written with a layer; a storage reads a missing one as null (Elasticsearch) or "" (BanyanDB).
            final String layer = (String) converter.get(LAYER);
            traffic.setLayer(StringUtil.isEmpty(layer) ? Layer.UNDEFINED.name() : layer);
            traffic.setTimeBucket(((Number) converter.get(TIME_BUCKET)).longValue());
            return traffic;
        }

        @Override
        public void entity2Storage(final OTLPServiceTraffic storageData, final Convert2Storage converter) {
            converter.accept(SERVICE_NAME, storageData.getServiceName());
            converter.accept(LAYER, storageData.getLayer());
            converter.accept(TIME_BUCKET, storageData.getTimeBucket());
        }
    }
}
