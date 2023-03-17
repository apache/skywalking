/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.skywalking.oap.server.core.analysis.manual.process;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.server.core.analysis.MetricsExtension;
import org.apache.skywalking.oap.server.core.analysis.Stream;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.analysis.worker.MetricsStreamProcessor;
import org.apache.skywalking.oap.server.core.remote.grpc.proto.RemoteData;
import org.apache.skywalking.oap.server.core.storage.StorageID;
import org.apache.skywalking.oap.server.core.storage.annotation.BanyanDB;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;
import org.apache.skywalking.oap.server.core.storage.type.Convert2Entity;
import org.apache.skywalking.oap.server.core.storage.type.Convert2Storage;
import org.apache.skywalking.oap.server.core.storage.type.StorageBuilder;

import static org.apache.skywalking.oap.server.core.source.DefaultScopeDefine.SERVICE_LABEL;

/**
 * Process have multiple labels, such as tag.
 * {@link ServiceLabelRecord} could combine them in the service level.
 * It could help to quickly locate the similar process by the service and label.
 */
@Setter
@Getter
@Stream(name = ServiceLabelRecord.INDEX_NAME, scopeId = SERVICE_LABEL,
        builder = ServiceLabelRecord.Builder.class, processor = MetricsStreamProcessor.class)
@MetricsExtension(supportDownSampling = false, supportUpdate = false, timeRelativeID = false)
@EqualsAndHashCode(of = {
        "serviceId",
        "label"
})
public class ServiceLabelRecord extends Metrics {

    public static final String INDEX_NAME = "service_label";
    public static final String SERVICE_ID = "service_id";
    public static final String LABEL = "label";

    @BanyanDB.SeriesID(index = 0)
    @Column(name = SERVICE_ID)
    private String serviceId;
    @BanyanDB.SeriesID(index = 1)
    @Column(name = LABEL, length = 50)
    private String label;

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
    protected StorageID id0() {
        return new StorageID()
            .append(SERVICE_ID, serviceId)
            .append(LABEL, label);
    }

    @Override
    public void deserialize(RemoteData remoteData) {
        setServiceId(remoteData.getDataStrings(0));
        setLabel(remoteData.getDataStrings(1));
        setTimeBucket(remoteData.getDataLongs(0));
    }

    @Override
    public RemoteData.Builder serialize() {
        final RemoteData.Builder builder = RemoteData.newBuilder();
        builder.addDataStrings(serviceId);
        builder.addDataStrings(label);
        builder.addDataLongs(getTimeBucket());
        return builder;
    }

    @Override
    public int remoteHashCode() {
        return this.hashCode();
    }

    public static class Builder implements StorageBuilder<ServiceLabelRecord> {

        @Override
        public ServiceLabelRecord storage2Entity(Convert2Entity converter) {
            final ServiceLabelRecord record = new ServiceLabelRecord();
            record.setServiceId((String) converter.get(SERVICE_ID));
            record.setLabel((String) converter.get(LABEL));
            record.setTimeBucket(((Number) converter.get(TIME_BUCKET)).longValue());
            return record;
        }

        @Override
        public void entity2Storage(ServiceLabelRecord entity, Convert2Storage converter) {
            converter.accept(SERVICE_ID, entity.getServiceId());
            converter.accept(LABEL, entity.getLabel());
            converter.accept(TIME_BUCKET, entity.getTimeBucket());
        }
    }
}
