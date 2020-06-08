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

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
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
import org.apache.skywalking.oap.server.core.remote.grpc.proto.RemoteData;
import org.apache.skywalking.oap.server.core.storage.StorageBuilder;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;

/**
 * AvgHistogram intends to aggregate raw values over the interval (minute, hour or day). When users query a value
 * from such a interval, an average over it will be sent back.
 *
 * The acceptable bucket value should be a result from one of "increase", "rate" and "irate" query functions.
 * That means the value is the increase or per-second instant rate of increase in a specific range.
 *
 * Example:
 * "persistence_timer_bulk_execute_latency" is histogram, the possible PromQL format of acceptable bucket value should be:
 * "increase(persistence_timer_bulk_execute_latency{service="oap-server", instance="localhost:1234"}[5m])"
 */
@MeterFunction(functionName = "avgHistogram")
@Slf4j
@ToString
public abstract class AvgHistogramFunction extends Metrics implements AcceptableValue<BucketedValues> {
    public static final String DATASET = "dataset";
    protected static final String SUMMATION = "summation";
    protected static final String COUNT = "count";

    @Setter
    @Getter
    @Column(columnName = ENTITY_ID)
    private String entityId;
    @Getter
    @Setter
    @Column(columnName = SUMMATION, storageOnly = true)
    protected DataTable summation = new DataTable(30);
    @Getter
    @Setter
    @Column(columnName = COUNT, storageOnly = true)
    protected DataTable count = new DataTable(30);
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
            String bucketName = String.valueOf(value.getBuckets()[i]);
            summation.valueAccumulation(bucketName, values[i]);
            count.valueAccumulation(bucketName, 1L);
        }
    }

    @Override
    public void combine(final Metrics metrics) {
        AvgHistogramFunction histogram = (AvgHistogramFunction) metrics;

        if (!summation.keysEqual(histogram.getSummation())) {
            log.warn("Incompatible input [{}}] for current HistogramFunction[{}], entity {}",
                     histogram, this, entityId
            );
            return;
        }
        this.summation.append(histogram.summation);
        this.count.append(histogram.count);
    }

    @Override
    public void calculate() {
        final List<String> sortedKeys = summation.sortedKeys(Comparator.comparingInt(Integer::parseInt));
        for (String key : sortedKeys) {
            dataset.put(key, summation.get(key) / count.get(key));
        }
    }

    @Override
    public Metrics toHour() {
        AvgHistogramFunction metrics = (AvgHistogramFunction) createNew();
        metrics.setEntityId(getEntityId());
        metrics.setTimeBucket(toTimeBucketInHour());
        metrics.setCount(getCount());
        metrics.setSummation(getSummation());
        return metrics;
    }

    @Override
    public Metrics toDay() {
        AvgHistogramFunction metrics = (AvgHistogramFunction) createNew();
        metrics.setEntityId(getEntityId());
        metrics.setTimeBucket(toTimeBucketInDay());
        metrics.setCount(getCount());
        metrics.setSummation(getSummation());
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

        this.setCount(new DataTable(remoteData.getDataObjectStrings(0)));
        this.setSummation(new DataTable(remoteData.getDataObjectStrings(1)));
    }

    @Override
    public RemoteData.Builder serialize() {
        RemoteData.Builder remoteBuilder = RemoteData.newBuilder();
        remoteBuilder.addDataLongs(getTimeBucket());

        remoteBuilder.addDataStrings(entityId);

        remoteBuilder.addDataObjectStrings(count.toStorageData());
        remoteBuilder.addDataObjectStrings(summation.toStorageData());

        return remoteBuilder;
    }

    @Override
    public String id() {
        return getTimeBucket() + Const.ID_CONNECTOR + entityId;
    }

    @Override
    public Class<? extends AvgHistogramFunctionBuilder> builder() {
        return AvgHistogramFunctionBuilder.class;
    }

    public static class AvgHistogramFunctionBuilder implements StorageBuilder<AvgHistogramFunction> {

        @Override
        public AvgHistogramFunction map2Data(final Map<String, Object> dbMap) {
            AvgHistogramFunction metrics = new AvgHistogramFunction() {
                @Override
                public AcceptableValue<BucketedValues> createNew() {
                    throw new UnexpectedException("createNew should not be called");
                }
            };
            metrics.setDataset(new DataTable((String) dbMap.get(DATASET)));
            metrics.setCount(new DataTable((String) dbMap.get(COUNT)));
            metrics.setSummation(new DataTable((String) dbMap.get(SUMMATION)));
            metrics.setTimeBucket(((Number) dbMap.get(TIME_BUCKET)).longValue());
            metrics.setEntityId((String) dbMap.get(ENTITY_ID));
            return metrics;
        }

        @Override
        public Map<String, Object> data2Map(final AvgHistogramFunction storageData) {
            Map<String, Object> map = new HashMap<>();
            map.put(DATASET, storageData.getDataset());
            map.put(COUNT, storageData.getCount());
            map.put(SUMMATION, storageData.getSummation());
            map.put(TIME_BUCKET, storageData.getTimeBucket());
            map.put(ENTITY_ID, storageData.getEntityId());
            return map;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof AvgHistogramFunction))
            return false;
        AvgHistogramFunction function = (AvgHistogramFunction) o;
        return Objects.equals(entityId, function.entityId) &&
            getTimeBucket() == function.getTimeBucket();
    }

    @Override
    public int hashCode() {
        return Objects.hash(entityId, getTimeBucket());
    }
}
