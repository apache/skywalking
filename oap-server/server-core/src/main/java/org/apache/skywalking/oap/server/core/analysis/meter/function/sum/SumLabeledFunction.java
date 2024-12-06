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

package org.apache.skywalking.oap.server.core.analysis.meter.function.sum;

import java.util.Objects;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.apache.skywalking.oap.server.core.UnexpectedException;
import org.apache.skywalking.oap.server.core.analysis.manual.instance.InstanceTraffic;
import org.apache.skywalking.oap.server.core.analysis.meter.Meter;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterEntity;
import org.apache.skywalking.oap.server.core.analysis.meter.function.AcceptableValue;
import org.apache.skywalking.oap.server.core.analysis.meter.function.MeterFunction;
import org.apache.skywalking.oap.server.core.analysis.metrics.DataTable;
import org.apache.skywalking.oap.server.core.analysis.metrics.LabeledValueHolder;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.remote.grpc.proto.RemoteData;
import org.apache.skywalking.oap.server.core.storage.StorageID;
import org.apache.skywalking.oap.server.core.storage.annotation.BanyanDB;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;
import org.apache.skywalking.oap.server.core.storage.annotation.ElasticSearch;
import org.apache.skywalking.oap.server.core.storage.type.Convert2Entity;
import org.apache.skywalking.oap.server.core.storage.type.Convert2Storage;
import org.apache.skywalking.oap.server.core.storage.type.StorageBuilder;

@MeterFunction(functionName = "sumLabeled")
@ToString
public abstract class SumLabeledFunction extends Meter implements AcceptableValue<DataTable>, LabeledValueHolder {

    protected static final String VALUE = "datatable_value";

    @Setter
    @Getter
    @ElasticSearch.EnableDocValues
    @Column(name = ENTITY_ID, length = 512)
    @BanyanDB.SeriesID(index = 0)
    private String entityId;

    @Setter
    @Getter
    @Column(name = InstanceTraffic.SERVICE_ID)
    private String serviceId;

    @Getter
    @Setter
    @Column(name = VALUE, dataType = Column.ValueDataType.LABELED_VALUE, storageOnly = true)
    @BanyanDB.MeasureField
    private DataTable value = new DataTable(30);

    @Override
    public final boolean combine(Metrics metrics) {
        SumLabeledFunction sumLabeledFunction = (SumLabeledFunction) metrics;
        this.value.append(sumLabeledFunction.value);
        return true;
    }

    @Override
    public final void calculate() {

    }

    @Override
    public Metrics toHour() {
        SumLabeledFunction metrics = (SumLabeledFunction) createNew();
        metrics.setEntityId(getEntityId());
        metrics.setTimeBucket(toTimeBucketInHour());
        metrics.setServiceId(getServiceId());
        metrics.getValue().copyFrom(getValue());
        return metrics;
    }

    @Override
    public Metrics toDay() {
        SumLabeledFunction metrics = (SumLabeledFunction) createNew();
        metrics.setEntityId(getEntityId());
        metrics.setTimeBucket(toTimeBucketInDay());
        metrics.setServiceId(getServiceId());
        metrics.getValue().copyFrom(getValue());
        return metrics;
    }

    @Override
    public int remoteHashCode() {
        return entityId.hashCode();
    }

    @Override
    public void deserialize(final RemoteData remoteData) {
        setValue(new DataTable(remoteData.getDataObjectStrings(0)));
        setTimeBucket(remoteData.getDataLongs(0));

        this.entityId = remoteData.getDataStrings(0);
        this.serviceId = remoteData.getDataStrings(1);
    }

    @Override
    public RemoteData.Builder serialize() {
        RemoteData.Builder remoteBuilder = RemoteData.newBuilder();
        remoteBuilder.addDataObjectStrings(value.toStorageData());
        remoteBuilder.addDataLongs(getTimeBucket());

        remoteBuilder.addDataStrings(entityId);
        remoteBuilder.addDataStrings(serviceId);

        return remoteBuilder;
    }

    @Override
    protected StorageID id0() {
        return new StorageID()
            .append(TIME_BUCKET, getTimeBucket())
            .append(ENTITY_ID, getEntityId());
    }

    @Override
    public void accept(final MeterEntity entity, final DataTable value) {
        this.entityId = entity.id();
        this.serviceId = entity.serviceId();
        this.value.append(value);
    }

    @Override
    public Class<? extends SumLabeledStorageBuilder> builder() {
        return SumLabeledStorageBuilder.class;
    }

    public static class SumLabeledStorageBuilder implements StorageBuilder<SumLabeledFunction> {
        @Override
        public SumLabeledFunction storage2Entity(final Convert2Entity converter) {
            SumLabeledFunction metrics = new SumLabeledFunction() {
                @Override
                public AcceptableValue<DataTable> createNew() {
                    throw new UnexpectedException("createNew should not be called");
                }
            };
            metrics.setValue(new DataTable((String) converter.get(VALUE)));
            metrics.setTimeBucket(((Number) converter.get(TIME_BUCKET)).longValue());
            metrics.setServiceId((String) converter.get(InstanceTraffic.SERVICE_ID));
            metrics.setEntityId((String) converter.get(ENTITY_ID));
            return metrics;
        }

        @Override
        public void entity2Storage(final SumLabeledFunction storageData, final Convert2Storage converter) {
            converter.accept(VALUE, storageData.getValue());
            converter.accept(TIME_BUCKET, storageData.getTimeBucket());
            converter.accept(InstanceTraffic.SERVICE_ID, storageData.getServiceId());
            converter.accept(ENTITY_ID, storageData.getEntityId());
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SumLabeledFunction)) {
            return false;
        }
        SumLabeledFunction function = (SumLabeledFunction) o;
        return Objects.equals(entityId, function.entityId) &&
            getTimeBucket() == function.getTimeBucket();
    }

    @Override
    public int hashCode() {
        return Objects.hash(entityId, getTimeBucket());
    }
}
