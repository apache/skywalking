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

import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.server.core.UnexpectedException;
import org.apache.skywalking.oap.server.core.analysis.manual.instance.InstanceTraffic;
import org.apache.skywalking.oap.server.core.analysis.meter.Meter;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterEntity;
import org.apache.skywalking.oap.server.core.analysis.meter.function.AcceptableValue;
import org.apache.skywalking.oap.server.core.analysis.meter.function.MeterFunction;
import org.apache.skywalking.oap.server.core.analysis.metrics.DataTable;
import org.apache.skywalking.oap.server.core.analysis.metrics.LabeledValueHolder;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.analysis.metrics.annotation.Entrance;
import org.apache.skywalking.oap.server.core.analysis.metrics.annotation.SourceFrom;
import org.apache.skywalking.oap.server.core.remote.grpc.proto.RemoteData;
import org.apache.skywalking.oap.server.core.storage.StorageID;
import org.apache.skywalking.oap.server.core.storage.annotation.BanyanDB;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;
import org.apache.skywalking.oap.server.core.storage.type.Convert2Entity;
import org.apache.skywalking.oap.server.core.storage.type.Convert2Storage;
import org.apache.skywalking.oap.server.core.storage.type.StorageBuilder;

import java.util.Objects;

@MeterFunction(functionName = "sumLabeled")
public abstract class SumLabeledFunction extends Meter implements AcceptableValue<DataTable>, LabeledValueHolder {

    protected static final String VALUE = "datatable_value";

    @Setter
    @Getter
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

    @Entrance
    public final void combine(@SourceFrom DataTable value) {
        this.value.append(value);
    }

    @Override
    public void accept(MeterEntity entity, DataTable value) {
        setEntityId(entity.id());
        setServiceId(entity.serviceId());
        this.value.append(value);
    }

    @Override
    public Class<? extends StorageBuilder> builder() {
        return SumLabeledStorageBuilder.class;
    }

    @Override
    public boolean combine(Metrics metrics) {
        final SumLabeledFunction sumLabeledFunction = (SumLabeledFunction) metrics;
        combine(sumLabeledFunction.getValue());
        return true;
    }

    @Override
    public void calculate() {
        for (String key : value.keys()) {
            final Long val = value.get(key);
            if (Objects.isNull(val)) {
                continue;
            }
            value.put(key, val);
        }
    }

    @Override
    public Metrics toHour() {
        final SumLabeledFunction metrics = (SumLabeledFunction) createNew();
        metrics.setEntityId(getEntityId());
        metrics.setTimeBucket(toTimeBucketInHour());
        metrics.setServiceId(getServiceId());
        metrics.getValue().copyFrom(getValue());
        return metrics;
    }

    @Override
    public Metrics toDay() {
        final SumLabeledFunction metrics = (SumLabeledFunction) createNew();
        metrics.setEntityId(getEntityId());
        metrics.setTimeBucket(toTimeBucketInDay());
        metrics.setServiceId(getServiceId());
        metrics.getValue().copyFrom(getValue());
        return metrics;
    }

    @Override
    protected StorageID id0() {
        return new StorageID()
            .append(TIME_BUCKET, getTimeBucket())
            .append(ENTITY_ID, getEntityId());
    }

    @Override
    public void deserialize(RemoteData remoteData) {
        setValue(new DataTable(remoteData.getDataObjectStrings(0)));
        setTimeBucket(remoteData.getDataLongs(0));

        setEntityId(remoteData.getDataStrings(0));
        setServiceId(remoteData.getDataStrings(1));
    }

    @Override
    public RemoteData.Builder serialize() {
        final RemoteData.Builder remoteBuilder = RemoteData.newBuilder();

        remoteBuilder.addDataObjectStrings(value.toStorageData());
        remoteBuilder.addDataLongs(getTimeBucket());

        remoteBuilder.addDataStrings(getEntityId());
        remoteBuilder.addDataStrings(getServiceId());
        return remoteBuilder;
    }

    @Override
    public int remoteHashCode() {
        return getEntityId().hashCode();
    }

    public static class SumLabeledStorageBuilder implements StorageBuilder<SumLabeledFunction> {
        @Override
        public SumLabeledFunction storage2Entity(Convert2Entity converter) {
            final SumLabeledFunction metrics = new SumLabeledFunction() {
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
        public void entity2Storage(SumLabeledFunction storageData, Convert2Storage converter) {
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
