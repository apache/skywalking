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

package org.apache.skywalking.oap.server.core.analysis.meter.function;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.UnexpectedException;
import org.apache.skywalking.oap.server.core.analysis.meter.Meter;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterEntity;
import org.apache.skywalking.oap.server.core.analysis.metrics.DataTable;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.query.type.Bucket;
import org.apache.skywalking.oap.server.core.remote.grpc.proto.RemoteData;
import org.apache.skywalking.oap.server.core.storage.StorageID;
import org.apache.skywalking.oap.server.core.storage.annotation.BanyanDB;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;
import org.apache.skywalking.oap.server.core.storage.type.Convert2Entity;
import org.apache.skywalking.oap.server.core.storage.type.Convert2Storage;
import org.apache.skywalking.oap.server.core.storage.type.StorageBuilder;

import java.util.Objects;

/**
 * Histogram includes data range buckets and the amount matched/grouped in the buckets. This is for original histogram
 * graph visualization
 */
@MeterFunction(functionName = "sumHistogram")
@Slf4j
@ToString
public abstract class HistogramFunction extends Meter implements AcceptableValue<BucketedValues> {
    public static final String DATASET = "dataset";

    @Setter
    @Getter
    @Column(name = ENTITY_ID, length = 512)
    @BanyanDB.SeriesID(index = 0)
    private String entityId;
    @Getter
    @Setter
    @Column(name = DATASET, dataType = Column.ValueDataType.HISTOGRAM, storageOnly = true, defaultValue = 0)
    @BanyanDB.MeasureField
    private DataTable dataset = new DataTable(30);

    @Override
    public void accept(final MeterEntity entity, final BucketedValues value) {
        if (dataset.size() > 0) {
            if (!value.isCompatible(dataset)) {
                throw new IllegalArgumentException(
                    "Incompatible BucketedValues [" + value + "] for current HistogramFunction[" + dataset + "]");
            }
        }

        this.entityId = entity.id();

        final long[] values = value.getValues();
        for (int i = 0; i < values.length; i++) {
            final long bucket = value.getBuckets()[i];
            String bucketName = bucket == Long.MIN_VALUE ? Bucket.INFINITE_NEGATIVE : String.valueOf(bucket);
            final long bucketValue = values[i];
            dataset.valueAccumulation(bucketName, bucketValue);
        }
    }

    @Override
    public boolean combine(final Metrics metrics) {
        HistogramFunction histogram = (HistogramFunction) metrics;

        if (!dataset.keysEqual(histogram.getDataset())) {
            log.warn("Incompatible input [{}}] for current HistogramFunction[{}], entity {}",
                     histogram, this, entityId
            );
            return true;
        }
        this.dataset.append(histogram.dataset);
        return true;
    }

    @Override
    public void calculate() {

    }

    @Override
    public Metrics toHour() {
        HistogramFunction metrics = (HistogramFunction) createNew();
        metrics.setEntityId(getEntityId());
        metrics.setTimeBucket(toTimeBucketInHour());
        metrics.getDataset().copyFrom(getDataset());
        return metrics;
    }

    @Override
    public Metrics toDay() {
        HistogramFunction metrics = (HistogramFunction) createNew();
        metrics.setEntityId(getEntityId());
        metrics.setTimeBucket(toTimeBucketInDay());
        metrics.getDataset().copyFrom(getDataset());
        return metrics;
    }

    @Override
    public int remoteHashCode() {
        return entityId.hashCode();
    }

    @Override
    public void deserialize(final RemoteData remoteData) {
        this.setTimeBucket(remoteData.getDataLongs(0));

        this.setEntityId(remoteData.getDataStrings(0));

        this.setDataset(new DataTable(remoteData.getDataObjectStrings(0)));
    }

    @Override
    public RemoteData.Builder serialize() {
        RemoteData.Builder remoteBuilder = RemoteData.newBuilder();
        remoteBuilder.addDataLongs(getTimeBucket());

        remoteBuilder.addDataStrings(entityId);

        remoteBuilder.addDataObjectStrings(dataset.toStorageData());

        return remoteBuilder;
    }

    @Override
    protected StorageID id0() {
        return new StorageID()
            .append(TIME_BUCKET, getTimeBucket())
            .append(ENTITY_ID, getEntityId());
    }

    @Override
    public Class<? extends HistogramFunctionBuilder> builder() {
        return HistogramFunctionBuilder.class;
    }

    public static class HistogramFunctionBuilder implements StorageBuilder<HistogramFunction> {
        @Override
        public HistogramFunction storage2Entity(final Convert2Entity converter) {
            HistogramFunction metrics = new HistogramFunction() {
                @Override
                public AcceptableValue<BucketedValues> createNew() {
                    throw new UnexpectedException("createNew should not be called");
                }
            };
            metrics.setDataset(new DataTable((String) converter.get(DATASET)));
            metrics.setTimeBucket(((Number) converter.get(TIME_BUCKET)).longValue());
            metrics.setEntityId((String) converter.get(ENTITY_ID));
            return metrics;
        }

        @Override
        public void entity2Storage(final HistogramFunction storageData, final Convert2Storage converter) {
            converter.accept(DATASET, storageData.getDataset());
            converter.accept(TIME_BUCKET, storageData.getTimeBucket());
            converter.accept(ENTITY_ID, storageData.getEntityId());
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof HistogramFunction))
            return false;
        HistogramFunction function = (HistogramFunction) o;
        return Objects.equals(entityId, function.entityId) &&
            getTimeBucket() == function.getTimeBucket();
    }

    @Override
    public int hashCode() {
        return Objects.hash(entityId, getTimeBucket());
    }
}
