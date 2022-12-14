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
import org.apache.skywalking.oap.server.core.Const;
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

@Stream(name = ZipkinServiceSpanTraffic.INDEX_NAME, scopeId = DefaultScopeDefine.ZIPKIN_SERVICE_SPAN,
    builder = ZipkinServiceSpanTraffic.Builder.class, processor = MetricsStreamProcessor.class)
@MetricsExtension(supportDownSampling = false, supportUpdate = false)
@EqualsAndHashCode(of = {
    "serviceName",
    "spanName"
})
@SQLDatabase.Sharding(shardingAlgorithm = ShardingAlgorithm.NO_SHARDING)
public class ZipkinServiceSpanTraffic extends Metrics {

    public static final String INDEX_NAME = "zipkin_service_span_traffic";

    public static final String SERVICE_NAME = "service_name";
    public static final String SPAN_NAME = "span_name";

    @Setter
    @Getter
    @Column(columnName = SERVICE_NAME)
    private String serviceName;
    @Setter
    @Getter
    @Column(columnName = SPAN_NAME)
    private String spanName = Const.EMPTY_STRING;

    @Override
    protected StorageID id0() {
        return new StorageID()
            .append(SERVICE_NAME, serviceName)
            .append(SPAN_NAME, spanName);
    }

    @Override
    public RemoteData.Builder serialize() {
        RemoteData.Builder remoteBuilder = RemoteData.newBuilder();
        remoteBuilder.addDataLongs(getTimeBucket());
        remoteBuilder.addDataStrings(serviceName);
        remoteBuilder.addDataStrings(spanName);
        return remoteBuilder;
    }

    @Override
    public void deserialize(RemoteData remoteData) {
        setTimeBucket(remoteData.getDataLongs(0));
        setServiceName(remoteData.getDataStrings(0));
        setSpanName(remoteData.getDataStrings(1));
    }

    @Override
    public int remoteHashCode() {
        return hashCode();
    }

    public static class Builder implements StorageBuilder<ZipkinServiceSpanTraffic> {
        @Override
        public ZipkinServiceSpanTraffic storage2Entity(final Convert2Entity converter) {
            ZipkinServiceSpanTraffic spanTraffic = new ZipkinServiceSpanTraffic();
            spanTraffic.setServiceName((String) converter.get(SERVICE_NAME));
            spanTraffic.setSpanName((String) converter.get(SPAN_NAME));
            spanTraffic.setTimeBucket(((Number) converter.get(TIME_BUCKET)).longValue());
            return spanTraffic;
        }

        @Override
        public void entity2Storage(final ZipkinServiceSpanTraffic storageData, final Convert2Storage converter) {
            converter.accept(SERVICE_NAME, storageData.getServiceName());
            converter.accept(SPAN_NAME, storageData.getSpanName());
            converter.accept(TIME_BUCKET, storageData.getTimeBucket());
        }
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
}
