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

package org.apache.skywalking.oap.server.core.analysis.meter.function.max;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.apache.skywalking.oap.server.core.UnexpectedException;
import org.apache.skywalking.oap.server.core.analysis.manual.instance.InstanceTraffic;
import org.apache.skywalking.oap.server.core.analysis.meter.Meter;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterEntity;
import org.apache.skywalking.oap.server.core.analysis.meter.function.AcceptableValue;
import org.apache.skywalking.oap.server.core.analysis.meter.function.MeterFunction;
import org.apache.skywalking.oap.server.core.analysis.metrics.LongValueHolder;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.analysis.metrics.annotation.Entrance;
import org.apache.skywalking.oap.server.core.analysis.metrics.annotation.SourceFrom;
import org.apache.skywalking.oap.server.core.query.sql.Function;
import org.apache.skywalking.oap.server.core.remote.grpc.proto.RemoteData;
import org.apache.skywalking.oap.server.core.storage.StorageID;
import org.apache.skywalking.oap.server.core.storage.annotation.BanyanDB;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;
import org.apache.skywalking.oap.server.core.storage.type.Convert2Entity;
import org.apache.skywalking.oap.server.core.storage.type.Convert2Storage;
import org.apache.skywalking.oap.server.core.storage.type.StorageBuilder;

import java.util.Objects;

@MeterFunction(functionName = "max")
@ToString
public abstract class MaxFunction extends Meter implements AcceptableValue<Long>, LongValueHolder {

    public static final String VALUE = "value";

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

    @Getter
    @Setter
    @Column(name = VALUE, dataType = Column.ValueDataType.COMMON_VALUE, function = Function.Avg)
    @BanyanDB.MeasureField
    private long value;

    @Override
    public void accept(MeterEntity entity, Long value) {
        setEntityId(entity.id());
        setServiceId(entity.serviceId());
        if (this.value < value) {
            setValue(value);
        }
    }

    @Entrance
    public final void combine(@SourceFrom long value) {
        if (this.value < value) {
            setValue(value);
        }
    }

    @Override
    public final boolean combine(Metrics metrics) {
        final MaxFunction maxFunction = (MaxFunction) metrics;
        combine(maxFunction.getValue());
        return true;
    }

    @Override
    public void calculate() {

    }

    @Override
    public Metrics toHour() {
        MaxFunction metrics = (MaxFunction) createNew();
        metrics.setEntityId(getEntityId());
        metrics.setTimeBucket(toTimeBucketInHour());
        metrics.setServiceId(getServiceId());
        metrics.setValue(getValue());
        return metrics;
    }

    @Override
    public Metrics toDay() {
        MaxFunction metrics = (MaxFunction) createNew();
        metrics.setEntityId(getEntityId());
        metrics.setTimeBucket(toTimeBucketInDay());
        metrics.setServiceId(getServiceId());
        metrics.setValue(getValue());
        return metrics;
    }

    @Override
    public int remoteHashCode() {
        return getEntityId().hashCode();
    }

    @Override
    public void deserialize(RemoteData remoteData) {
        setEntityId(remoteData.getDataStrings(0));
        setServiceId(remoteData.getDataStrings(1));
        setTimeBucket(remoteData.getDataLongs(1));
        setValue(remoteData.getDataLongs(0));
    }

    @Override
    public RemoteData.Builder serialize() {
        final RemoteData.Builder remoteBuilder = RemoteData.newBuilder();
        remoteBuilder.addDataLongs(getValue());
        remoteBuilder.addDataLongs(getTimeBucket());
        remoteBuilder.addDataStrings(getEntityId());
        remoteBuilder.addDataStrings(getServiceId());
        return remoteBuilder;
    }

    @Override
    protected StorageID id0() {
        return new StorageID()
                .append(TIME_BUCKET, getTimeBucket())
                .append(ENTITY_ID, getEntityId());
    }

    @Override
    public Class<? extends StorageBuilder> builder() {
        return MaxStorageBuilder.class;
    }

    public static class MaxStorageBuilder implements StorageBuilder<MaxFunction> {

        @Override
        public MaxFunction storage2Entity(Convert2Entity converter) {
            MaxFunction metrics = new MaxFunction() {
                @Override
                public AcceptableValue<Long> createNew() {
                    throw new UnexpectedException("createNew should not be called");
                }
            };
            metrics.setEntityId((String) converter.get(ENTITY_ID));
            metrics.setServiceId((String) converter.get(InstanceTraffic.SERVICE_ID));
            metrics.setTimeBucket(((Number) converter.get(TIME_BUCKET)).longValue());
            metrics.setValue(((Number) converter.get(VALUE)).longValue());
            return metrics;
        }

        @Override
        public void entity2Storage(final MaxFunction storageData, final Convert2Storage converter) {
            converter.accept(ENTITY_ID, storageData.getEntityId());
            converter.accept(InstanceTraffic.SERVICE_ID, storageData.getServiceId());
            converter.accept(TIME_BUCKET, storageData.getTimeBucket());
            converter.accept(VALUE, storageData.getValue());
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MaxFunction)) {
            return false;
        }
        final MaxFunction function = (MaxFunction) o;
        return Objects.equals(getEntityId(), function.getEntityId())
                && Objects.equals(getTimeBucket(), function.getTimeBucket());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getEntityId(), getTimeBucket());
    }
}
