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

package org.apache.skywalking.oap.server.core.zipkin;

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
import org.apache.skywalking.oap.server.core.storage.type.Convert2Entity;
import org.apache.skywalking.oap.server.core.storage.type.Convert2Storage;
import org.apache.skywalking.oap.server.core.storage.type.StorageBuilder;

@Stream(name = ZipkinServiceRelationTraffic.INDEX_NAME, scopeId = DefaultScopeDefine.ZIPKIN_SERVICE_RELATION,
    builder = ZipkinServiceRelationTraffic.Builder.class, processor = MetricsStreamProcessor.class)
@MetricsExtension(supportDownSampling = false, supportUpdate = false)
@EqualsAndHashCode(of = {
    "serviceName",
    "remoteServiceName"
})
@BanyanDB.IndexMode
public class ZipkinServiceRelationTraffic extends Metrics {

    public static final String INDEX_NAME = "zipkin_service_relation_traffic";
    public static final String SERVICE_NAME = "service_name";
    public static final String REMOTE_SERVICE_NAME = "remote_service_name";

    @Setter
    @Getter
    @Column(name = SERVICE_NAME)
    private String serviceName;
    @Setter
    @Getter
    @Column(name = REMOTE_SERVICE_NAME)
    private String remoteServiceName;

    @Override
    protected StorageID id0() {
        return new StorageID().append(serviceName).append(remoteServiceName);
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
        return null;
    }

    @Override
    public Metrics toDay() {
        return null;
    }

    @Override
    public int remoteHashCode() {
        return this.hashCode();
    }

    @Override
    public void deserialize(RemoteData remoteData) {
        setServiceName(remoteData.getDataStrings(0));
        setRemoteServiceName(remoteData.getDataStrings(1));
        setTimeBucket(remoteData.getDataLongs(0));
    }

    @Override
    public RemoteData.Builder serialize() {
        RemoteData.Builder remoteBuilder = RemoteData.newBuilder();
        remoteBuilder.addDataStrings(getServiceName());
        remoteBuilder.addDataStrings(getRemoteServiceName());

        remoteBuilder.addDataLongs(getTimeBucket());
        return remoteBuilder;
    }

    public static class Builder implements StorageBuilder<ZipkinServiceRelationTraffic> {
        @Override
        public ZipkinServiceRelationTraffic storage2Entity(final Convert2Entity converter) {
            ZipkinServiceRelationTraffic metrics = new ZipkinServiceRelationTraffic();
            metrics.setServiceName((String) converter.get(SERVICE_NAME));
            metrics.setRemoteServiceName((String) converter.get(REMOTE_SERVICE_NAME));
            metrics.setTimeBucket(((Number) converter.get(TIME_BUCKET)).longValue());
            return metrics;
        }

        @Override
        public void entity2Storage(final ZipkinServiceRelationTraffic storageData,
                                   final Convert2Storage converter) {
            converter.accept(TIME_BUCKET, storageData.getTimeBucket());
            converter.accept(SERVICE_NAME, storageData.getServiceName());
            converter.accept(REMOTE_SERVICE_NAME, storageData.getRemoteServiceName());
        }
    }
}
