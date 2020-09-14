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

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.UnexpectedException;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterEntity;
import org.apache.skywalking.oap.server.core.analysis.metrics.DataTable;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.query.type.Bucket;
import org.apache.skywalking.oap.server.core.remote.grpc.proto.RemoteData;
import org.apache.skywalking.oap.server.core.storage.StorageBuilder;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;

/**
 * Histogram includes data range buckets and the amount matched/grouped in the buckets. This is for original histogram
 * graph visualization
 */
@MeterFunction(functionName = "histogram")
@Slf4j
@ToString
public abstract class HistogramFunction extends Metrics implements AcceptableValue<BucketedValues> {
    public static final String DATASET = "dataset";

    @Setter
    @Getter
    @Column(columnName = ENTITY_ID, length = 512)
    private String entityId;
    @Getter
    @Setter
    @Column(columnName = DATASET, dataType = Column.ValueDataType.HISTOGRAM, storageOnly = true, defaultValue = 0)
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
    public void combine(final Metrics metrics) {
        HistogramFunction histogram = (HistogramFunction) metrics;

        if (!dataset.keysEqual(histogram.getDataset())) {
            log.warn("Incompatible input [{}}] for current HistogramFunction[{}], entity {}",
                     histogram, this, entityId
            );
            return;
        }
        this.dataset.append(histogram.dataset);
    }

    @Override
    public void calculate() {

    }

    @Override
    public Metrics toHour() {
        HistogramFunction metrics = (HistogramFunction) createNew();
        metrics.setEntityId(getEntityId());
        metrics.setTimeBucket(toTimeBucketInHour());
        metrics.setDataset(getDataset());
        return metrics;
    }

    @Override
    public Metrics toDay() {
        HistogramFunction metrics = (HistogramFunction) createNew();
        metrics.setEntityId(getEntityId());
        metrics.setTimeBucket(toTimeBucketInDay());
        metrics.setDataset(getDataset());
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
    public String id() {
        return getTimeBucket() + Const.ID_CONNECTOR + entityId;
    }

    @Override
    public Class<? extends StorageBuilder> builder() {
        return HistogramFunctionBuilder.class;
    }

    public static class HistogramFunctionBuilder implements StorageBuilder<HistogramFunction> {

        @Override
        public HistogramFunction map2Data(final Map<String, Object> dbMap) {
            HistogramFunction metrics = new HistogramFunction() {
                @Override
                public AcceptableValue<BucketedValues> createNew() {
                    throw new UnexpectedException("createNew should not be called");
                }
            };
            metrics.setDataset(new DataTable((String) dbMap.get(DATASET)));
            metrics.setTimeBucket(((Number) dbMap.get(TIME_BUCKET)).longValue());
            metrics.setEntityId((String) dbMap.get(ENTITY_ID));
            return metrics;
        }

        @Override
        public Map<String, Object> data2Map(final HistogramFunction storageData) {
            Map<String, Object> map = new HashMap<>();
            map.put(DATASET, storageData.getDataset());
            map.put(TIME_BUCKET, storageData.getTimeBucket());
            map.put(ENTITY_ID, storageData.getEntityId());
            return map;
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
