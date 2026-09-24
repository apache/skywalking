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

/**
 * The service-name catalog of natively stored OTLP spans, one row per {@code resource.service.name}. Feeds the
 * TraceQL {@code resource.service.name} value list.
 */
@Stream(name = OTLPServiceTraffic.INDEX_NAME, scopeId = DefaultScopeDefine.OTLP_SERVICE,
    builder = OTLPServiceTraffic.Builder.class, processor = MetricsStreamProcessor.class)
@MetricsExtension(supportDownSampling = false, supportUpdate = false)
@EqualsAndHashCode(of = {
    "serviceName"
})
@BanyanDB.IndexMode
public class OTLPServiceTraffic extends Metrics {
    public static final String INDEX_NAME = "otlp_service_traffic";
    public static final String SERVICE_NAME = "service_name";

    @Setter
    @Getter
    @Column(name = SERVICE_NAME)
    private String serviceName = Const.EMPTY_STRING;

    @Override
    protected StorageID id0() {
        return new StorageID().append(SERVICE_NAME, serviceName);
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
        setTimeBucket(remoteData.getDataLongs(0));
    }

    @Override
    public RemoteData.Builder serialize() {
        final RemoteData.Builder remoteBuilder = RemoteData.newBuilder();
        remoteBuilder.addDataStrings(getServiceName());
        remoteBuilder.addDataLongs(getTimeBucket());
        return remoteBuilder;
    }

    public static class Builder implements StorageBuilder<OTLPServiceTraffic> {
        @Override
        public OTLPServiceTraffic storage2Entity(final Convert2Entity converter) {
            final OTLPServiceTraffic traffic = new OTLPServiceTraffic();
            traffic.setServiceName((String) converter.get(SERVICE_NAME));
            traffic.setTimeBucket(((Number) converter.get(TIME_BUCKET)).longValue());
            return traffic;
        }

        @Override
        public void entity2Storage(final OTLPServiceTraffic storageData, final Convert2Storage converter) {
            converter.accept(SERVICE_NAME, storageData.getServiceName());
            converter.accept(TIME_BUCKET, storageData.getTimeBucket());
        }
    }
}
