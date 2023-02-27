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

package org.apache.skywalking.oap.server.core.analysis.meter.function.sumpermin;

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

@ToString
@MeterFunction(functionName = "sumPerMin")
@BanyanDB.TopNAggregation(groupByTagNames = {Metrics.ENTITY_ID, InstanceTraffic.SERVICE_ID})
public abstract class SumPerMinFunction extends Meter implements AcceptableValue<Long>, LongValueHolder {
    protected static final String VALUE = "value";
    protected static final String TOTAL = "total";

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
    @Column(name = VALUE, dataType = Column.ValueDataType.COMMON_VALUE, function = Function.Avg)
    @BanyanDB.MeasureField
    private long value;

    @Getter
    @Setter
    @Column(name = TOTAL, storageOnly = true)
    @BanyanDB.MeasureField
    private long total;

    @Entrance
    public final void combine(@SourceFrom long value) {
        this.total += value;
    }

    @Override
    public boolean combine(Metrics metrics) {
        final SumPerMinFunction sumPerMinFunction = (SumPerMinFunction) metrics;
        combine(sumPerMinFunction.getTotal());
        return true;
    }

    @Override
    public void calculate() {
        setValue(this.total / getDurationInMinute());
    }

    @Override
    public Metrics toHour() {
        final SumPerMinFunction metrics = (SumPerMinFunction) createNew();
        metrics.setEntityId(getEntityId());
        metrics.setTimeBucket(toTimeBucketInHour());
        metrics.setServiceId(getServiceId());
        metrics.setTotal(getTotal());
        return metrics;
    }

    @Override
    public Metrics toDay() {
        final SumPerMinFunction metrics = (SumPerMinFunction) createNew();
        metrics.setEntityId(getEntityId());
        metrics.setTimeBucket(toTimeBucketInDay());
        metrics.setServiceId(getServiceId());
        metrics.setTotal(getTotal());
        return metrics;
    }

    @Override
    public int remoteHashCode() {
        return getEntityId().hashCode();
    }

    @Override
    public void deserialize(RemoteData remoteData) {
        setTotal(remoteData.getDataLongs(0));
        setTimeBucket(remoteData.getDataLongs(1));

        setEntityId(remoteData.getDataStrings(0));
        setServiceId(remoteData.getDataStrings(1));
    }

    @Override
    public RemoteData.Builder serialize() {
        final RemoteData.Builder remoteBuilder = RemoteData.newBuilder();

        remoteBuilder.addDataLongs(getTotal());
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
    public void accept(MeterEntity entity, Long value) {
        setEntityId(entity.id());
        setServiceId(entity.serviceId());
        setTotal(getTotal() + value);
    }

    @Override
    public Class<? extends StorageBuilder> builder() {
        return SumPerMinStorageBuilder.class;
    }

    public static class SumPerMinStorageBuilder implements StorageBuilder<SumPerMinFunction> {
        @Override
        public SumPerMinFunction storage2Entity(final Convert2Entity converter) {
            final SumPerMinFunction metrics = new SumPerMinFunction() {
                @Override
                public AcceptableValue<Long> createNew() {
                    throw new UnexpectedException("createNew should not be called");
                }
            };
            metrics.setValue(((Number) converter.get(VALUE)).longValue());
            metrics.setTotal(((Number) converter.get(TOTAL)).longValue());
            metrics.setTimeBucket(((Number) converter.get(TIME_BUCKET)).longValue());
            metrics.setServiceId((String) converter.get(InstanceTraffic.SERVICE_ID));
            metrics.setEntityId((String) converter.get(ENTITY_ID));
            return metrics;
        }

        @Override
        public void entity2Storage(final SumPerMinFunction storageData, final Convert2Storage converter) {
            converter.accept(VALUE, storageData.getValue());
            converter.accept(TOTAL, storageData.getTotal());
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
        if (!(o instanceof SumPerMinFunction)) {
            return false;
        }
        final SumPerMinFunction function = (SumPerMinFunction) o;
        return Objects.equals(getEntityId(), function.getEntityId())
            && Objects.equals(getTimeBucket(), function.getTimeBucket());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getEntityId(), getTimeBucket());
    }
}
