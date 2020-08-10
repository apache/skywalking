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
import java.util.stream.IntStream;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.UnexpectedException;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterEntity;
import org.apache.skywalking.oap.server.core.analysis.metrics.DataTable;
import org.apache.skywalking.oap.server.core.analysis.metrics.IntList;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.analysis.metrics.MultiIntValuesHolder;
import org.apache.skywalking.oap.server.core.remote.grpc.proto.RemoteData;
import org.apache.skywalking.oap.server.core.storage.StorageBuilder;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;

/**
 * AvgPercentile intends to calculate percentile based on the average of raw values over the interval(minute, hour or day).
 *
 * The acceptable bucket value should be a result from one of "increase", "rate" and "irate" query functions.
 * That means the value in a bucket is the increase or per-second instant rate of increase in a specific range.
 * Then AvgPercentileFunction calculates percentile based on the above buckets.
 *
 * Example:
 * "persistence_timer_bulk_execute_latency" is histogram, the possible PromQL format of acceptable bucket value should be:
 * "increase(persistence_timer_bulk_execute_latency{service="oap-server", instance="localhost:1234"}[5m])"
 */
@MeterFunction(functionName = "avgHistogramPercentile")
@Slf4j
public abstract class AvgHistogramPercentileFunction extends Metrics implements AcceptableValue<AvgHistogramPercentileFunction.AvgPercentileArgument>, MultiIntValuesHolder {
    public static final String DATASET = "dataset";
    public static final String RANKS = "ranks";
    public static final String VALUE = "value";
    protected static final String SUMMATION = "summation";
    protected static final String COUNT = "count";

    @Setter
    @Getter
    @Column(columnName = ENTITY_ID)
    private String entityId;
    @Getter
    @Setter
    @Column(columnName = VALUE, dataType = Column.ValueDataType.LABELED_VALUE, storageOnly = true)
    private DataTable percentileValues = new DataTable(10);
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
    @Column(columnName = DATASET, storageOnly = true)
    private DataTable dataset = new DataTable(30);
    /**
     * Rank
     */
    @Getter
    @Setter
    @Column(columnName = RANKS, storageOnly = true)
    private IntList ranks = new IntList(10);

    private boolean isCalculated = false;

    @Override
    public void accept(final MeterEntity entity, final AvgPercentileArgument value) {
        if (dataset.size() > 0) {
            if (!value.getBucketedValues().isCompatible(dataset)) {
                throw new IllegalArgumentException(
                    "Incompatible BucketedValues [" + value + "] for current PercentileFunction[" + dataset + "]");
            }
        }

        for (final int rank : value.getRanks()) {
            if (rank <= 0) {
                throw new IllegalArgumentException("Illegal rank value " + rank + ", must be positive");
            }
        }

        if (ranks.size() > 0) {
            if (ranks.size() != value.getRanks().length) {
                throw new IllegalArgumentException(
                    "Incompatible ranks size = [" + value.getRanks().length + "] for current PercentileFunction[" + ranks
                        .size() + "]");
            } else {
                for (final int rank : value.getRanks()) {
                    if (!ranks.include(rank)) {
                        throw new IllegalArgumentException(
                            "Rank " + rank + " doesn't exist in the previous ranks " + ranks);
                    }
                }
            }
        } else {
            for (final int rank : value.getRanks()) {
                ranks.add(rank);
            }
        }

        this.entityId = entity.id();

        final long[] values = value.getBucketedValues().getValues();
        for (int i = 0; i < values.length; i++) {
            String bucketName = String.valueOf(value.getBucketedValues().getBuckets()[i]);
            summation.valueAccumulation(bucketName, values[i]);
            count.valueAccumulation(bucketName, 1L);
        }

        this.isCalculated = false;
    }

    @Override
    public void combine(final Metrics metrics) {
        AvgHistogramPercentileFunction percentile = (AvgHistogramPercentileFunction) metrics;

        if (!summation.keysEqual(percentile.getSummation())) {
            log.warn("Incompatible input [{}}] for current PercentileFunction[{}], entity {}",
                     percentile, this, entityId
            );
            return;
        }
        if (ranks.size() > 0) {
            if (this.ranks.size() != ranks.size()) {
                log.warn("Incompatible ranks size = [{}}] for current PercentileFunction[{}]",
                         ranks.size(), this.ranks.size()
                );
                return;
            } else {
                if (!this.ranks.equals(percentile.getRanks())) {
                    log.warn("Rank {} doesn't exist in the previous ranks {}", percentile.getRanks(), ranks);
                    return;
                }
            }
        }

        this.summation.append(percentile.summation);
        this.count.append(percentile.count);

        this.isCalculated = false;
    }

    @Override
    public void calculate() {
        if (!isCalculated) {
            final List<String> sortedKeys = summation.sortedKeys(Comparator.comparingInt(Integer::parseInt));
            for (String key : sortedKeys) {
                dataset.put(key, summation.get(key) / count.get(key));
            }

            long total = dataset.sumOfValues();

            int[] roofs = new int[ranks.size()];
            for (int i = 0; i < ranks.size(); i++) {
                roofs[i] = Math.round(total * ranks.get(i) * 1.0f / 100);
            }

            int count = 0;
            int loopIndex = 0;

            for (int i = 0; i < sortedKeys.size(); i++) {
                String key = sortedKeys.get(i);
                final Long value = dataset.get(key);

                count += value;
                for (int rankIdx = loopIndex; rankIdx < roofs.length; rankIdx++) {
                    int roof = roofs[rankIdx];

                    if (count >= roof) {
                        long latency = (i + 1 == sortedKeys.size()) ? Long.MAX_VALUE : Long.parseLong(sortedKeys.get(i + 1));
                        percentileValues.put(String.valueOf(ranks.get(rankIdx)), latency);
                        loopIndex++;
                    } else {
                        break;
                    }
                }
            }
        }
    }

    @Override
    public Metrics toHour() {
        AvgHistogramPercentileFunction metrics = (AvgHistogramPercentileFunction) createNew();
        metrics.setEntityId(getEntityId());
        metrics.setTimeBucket(toTimeBucketInHour());
        metrics.setSummation(getSummation());
        metrics.setCount(getCount());
        metrics.setRanks(getRanks());
        metrics.setPercentileValues(getPercentileValues());
        return metrics;
    }

    @Override
    public Metrics toDay() {
        AvgHistogramPercentileFunction metrics = (AvgHistogramPercentileFunction) createNew();
        metrics.setEntityId(getEntityId());
        metrics.setTimeBucket(toTimeBucketInDay());
        metrics.setSummation(getSummation());
        metrics.setCount(getCount());
        metrics.setRanks(getRanks());
        metrics.setPercentileValues(getPercentileValues());
        return metrics;
    }

    @Override
    public int[] getValues() {
        return percentileValues.sortedValues(Comparator.comparingInt(Integer::parseInt))
                               .stream()
                               .flatMapToInt(l -> IntStream.of(l.intValue()))
                               .toArray();
    }

    @Override
    public int remoteHashCode() {
        return entityId.hashCode();
    }

    @Override
    public void deserialize(final RemoteData remoteData) {
        this.setTimeBucket(remoteData.getDataLongs(0));

        this.setEntityId(remoteData.getDataStrings(0));

        this.setSummation(new DataTable(remoteData.getDataObjectStrings(0)));
        this.setCount(new DataTable(remoteData.getDataObjectStrings(1)));
        this.setRanks(new IntList(remoteData.getDataObjectStrings(2)));
        this.setPercentileValues(new DataTable(remoteData.getDataObjectStrings(3)));
    }

    @Override
    public RemoteData.Builder serialize() {
        RemoteData.Builder remoteBuilder = RemoteData.newBuilder();
        remoteBuilder.addDataLongs(getTimeBucket());

        remoteBuilder.addDataStrings(entityId);

        remoteBuilder.addDataObjectStrings(summation.toStorageData());
        remoteBuilder.addDataObjectStrings(count.toStorageData());
        remoteBuilder.addDataObjectStrings(ranks.toStorageData());
        remoteBuilder.addDataObjectStrings(percentileValues.toStorageData());

        return remoteBuilder;
    }

    @Override
    public String id() {
        return getTimeBucket() + Const.ID_CONNECTOR + entityId;
    }

    @Override
    public Class<? extends AvgPercentileFunctionBuilder> builder() {
        return AvgPercentileFunctionBuilder.class;
    }

    @RequiredArgsConstructor
    @Getter
    public static class AvgPercentileArgument {
        private final BucketedValues bucketedValues;
        private final int[] ranks;
    }

    public static class AvgPercentileFunctionBuilder implements StorageBuilder<AvgHistogramPercentileFunction> {

        @Override
        public AvgHistogramPercentileFunction map2Data(final Map<String, Object> dbMap) {
            AvgHistogramPercentileFunction metrics = new AvgHistogramPercentileFunction() {
                @Override
                public AcceptableValue<AvgPercentileArgument> createNew() {
                    throw new UnexpectedException("createNew should not be called");
                }
            };
            metrics.setDataset(new DataTable((String) dbMap.get(DATASET)));
            metrics.setSummation(new DataTable((String) dbMap.get(SUMMATION)));
            metrics.setCount(new DataTable((String) dbMap.get(COUNT)));
            metrics.setRanks(new IntList((String) dbMap.get(RANKS)));
            metrics.setPercentileValues(new DataTable((String) dbMap.get(VALUE)));
            metrics.setTimeBucket(((Number) dbMap.get(TIME_BUCKET)).longValue());
            metrics.setEntityId((String) dbMap.get(ENTITY_ID));
            return metrics;
        }

        @Override
        public Map<String, Object> data2Map(final AvgHistogramPercentileFunction storageData) {
            Map<String, Object> map = new HashMap<>();
            map.put(SUMMATION, storageData.getSummation());
            map.put(COUNT, storageData.getCount());
            map.put(DATASET, storageData.getDataset());
            map.put(RANKS, storageData.getRanks());
            map.put(VALUE, storageData.getPercentileValues());
            map.put(TIME_BUCKET, storageData.getTimeBucket());
            map.put(ENTITY_ID, storageData.getEntityId());
            return map;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof AvgHistogramPercentileFunction))
            return false;
        AvgHistogramPercentileFunction function = (AvgHistogramPercentileFunction) o;
        return Objects.equals(entityId, function.entityId) &&
            getTimeBucket() == function.getTimeBucket();
    }

    @Override
    public int hashCode() {
        return Objects.hash(entityId, getTimeBucket());
    }
}
