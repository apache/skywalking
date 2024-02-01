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

package org.apache.skywalking.oap.server.core.analysis.meter.function.count;

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
import org.apache.skywalking.oap.server.core.analysis.metrics.LongValueHolder;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.remote.grpc.proto.RemoteData;
import org.apache.skywalking.oap.server.core.storage.StorageID;
import org.apache.skywalking.oap.server.core.storage.annotation.BanyanDB;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;
import org.apache.skywalking.oap.server.core.storage.type.Convert2Entity;
import org.apache.skywalking.oap.server.core.storage.type.Convert2Storage;
import org.apache.skywalking.oap.server.core.storage.type.StorageBuilder;

import java.util.Objects;

@MeterFunction(functionName = "countLabeled")
@ToString
public abstract class CountLabeledFunction extends Meter implements AcceptableValue<DataTable>, LongValueHolder {

    public static final String VALUE = "datatable_value";

    @Setter
    @Getter
    @Column(name = ENTITY_ID, length = 512)
    @BanyanDB.SeriesID(index = 0)
    private String entityId;

    /**
     * Service ID is required for sort query.
     */
    @Setter
    @Getter
    @Column(name = InstanceTraffic.SERVICE_ID)
    private String serviceId;

    private DataTable dataTable = new DataTable(30);

    @Getter
    @Setter
    @Column(name = VALUE, dataType = Column.ValueDataType.COMMON_VALUE)
    @BanyanDB.MeasureField
    private long value;

    @Override
    public void accept(final MeterEntity entity, final DataTable value) {
        setEntityId(entity.id());
        setServiceId(entity.serviceId());
        this.dataTable.append(value);
        this.value = this.dataTable.size();
    }

    @Override
    public final boolean combine(Metrics metrics) {
        final CountLabeledFunction countLabeledFunction = (CountLabeledFunction) metrics;
        this.dataTable.append(countLabeledFunction.dataTable);
        this.value = this.dataTable.size();
        return true;
    }

    @Override
    public final void calculate() {

    }

    @Override
    public Metrics toHour() {
        CountLabeledFunction metrics = (CountLabeledFunction) createNew();
        metrics.setEntityId(getEntityId());
        metrics.setTimeBucket(toTimeBucketInHour());
        metrics.setServiceId(getServiceId());
        metrics.setValue(getValue());
        return metrics;
    }

    @Override
    public Metrics toDay() {
        CountLabeledFunction metrics = (CountLabeledFunction) createNew();
        metrics.setEntityId(getEntityId());
        metrics.setTimeBucket(toTimeBucketInDay());
        metrics.setServiceId(getServiceId());
        metrics.setValue(getValue());
        return metrics;
    }

    @Override
    protected StorageID id0() {
        return new StorageID()
                .append(TIME_BUCKET, getTimeBucket())
                .append(ENTITY_ID, getEntityId());
    }

    @Override
    public void deserialize(final RemoteData remoteData) {
        setValue(remoteData.getDataLongs(0));
        setTimeBucket(remoteData.getDataLongs(1));

        setEntityId(remoteData.getDataStrings(0));
        setServiceId(remoteData.getDataStrings(1));
    }

    @Override
    public RemoteData.Builder serialize() {
        final RemoteData.Builder remoteBuilder = RemoteData.newBuilder();
        remoteBuilder.addDataLongs(getValue());
        remoteBuilder.addDataLongs(getTimeBucket());

        remoteBuilder.addDataStrings(entityId);
        remoteBuilder.addDataStrings(serviceId);

        return remoteBuilder;
    }

    @Override
    public int remoteHashCode() {
        return entityId.hashCode();
    }

    @Override
    public Class<? extends CountLabeledStorageBuilder> builder() {
        return CountLabeledStorageBuilder.class;
    }

    public static class CountLabeledStorageBuilder implements StorageBuilder<CountLabeledFunction> {
        @Override
        public CountLabeledFunction storage2Entity(final Convert2Entity converter) {
            CountLabeledFunction metrics = new CountLabeledFunction() {
                @Override
                public AcceptableValue<DataTable> createNew() {
                    throw new UnexpectedException("createNew should not be called");
                }
            };
            metrics.setValue(((Number) converter.get(VALUE)).longValue());
            metrics.setTimeBucket(((Number) converter.get(TIME_BUCKET)).longValue());
            metrics.setServiceId((String) converter.get(InstanceTraffic.SERVICE_ID));
            metrics.setEntityId((String) converter.get(ENTITY_ID));
            return metrics;
        }

        @Override
        public void entity2Storage(final CountLabeledFunction storageData, final Convert2Storage converter) {
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
        if (!(o instanceof CountLabeledFunction)) {
            return false;
        }
        CountLabeledFunction function = (CountLabeledFunction) o;
        return Objects.equals(entityId, function.entityId) &&
                getTimeBucket() == function.getTimeBucket();
    }

    @Override
    public int hashCode() {
        return Objects.hash(entityId, getTimeBucket());
    }
}
