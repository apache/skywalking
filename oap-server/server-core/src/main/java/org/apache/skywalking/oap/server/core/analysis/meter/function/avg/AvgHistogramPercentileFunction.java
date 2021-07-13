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

package org.apache.skywalking.oap.server.core.analysis.meter.function.avg;

import com.google.common.base.Strings;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collector;
import java.util.stream.IntStream;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.UnexpectedException;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterEntity;
import org.apache.skywalking.oap.server.core.analysis.meter.function.AcceptableValue;
import org.apache.skywalking.oap.server.core.analysis.meter.function.MeterFunction;
import org.apache.skywalking.oap.server.core.analysis.meter.function.PercentileArgument;
import org.apache.skywalking.oap.server.core.analysis.metrics.DataTable;
import org.apache.skywalking.oap.server.core.analysis.metrics.IntList;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.analysis.metrics.MultiIntValuesHolder;
import org.apache.skywalking.oap.server.core.query.type.Bucket;
import org.apache.skywalking.oap.server.core.remote.grpc.proto.RemoteData;
import org.apache.skywalking.oap.server.core.storage.StorageHashMapBuilder;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;

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
public abstract class AvgHistogramPercentileFunction extends Metrics implements AcceptableValue<PercentileArgument>, MultiIntValuesHolder {
    private static final String DEFAULT_GROUP = "pD";
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
    public void accept(final MeterEntity entity, final PercentileArgument value) {
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

        String template = "%s";
        if (!Strings.isNullOrEmpty(value.getBucketedValues().getGroup())) {
            template  = value.getBucketedValues().getGroup() + ":%s";
        }
        final long[] values = value.getBucketedValues().getValues();
        for (int i = 0; i < values.length; i++) {
            long bucket = value.getBucketedValues().getBuckets()[i];
            String bucketName = bucket == Long.MIN_VALUE ? Bucket.INFINITE_NEGATIVE : String.valueOf(bucket);
            String key = String.format(template, bucketName);
            summation.valueAccumulation(key, values[i]);
            count.valueAccumulation(key, 1L);
        }

        this.isCalculated = false;
    }

    @Override
    public boolean combine(final Metrics metrics) {
        AvgHistogramPercentileFunction percentile = (AvgHistogramPercentileFunction) metrics;

        if (this.ranks.size() > 0) {
            IntList ranksOfThat = percentile.getRanks();
            if (this.ranks.size() != ranksOfThat.size()) {
                log.warn("Incompatible ranks size = [{}}] for current PercentileFunction[{}]",
                         ranksOfThat.size(), this.ranks.size()
                );
                return true;
            } else {
                if (!this.ranks.equals(ranksOfThat)) {
                    log.warn("Rank {} doesn't exist in the previous ranks {}", ranksOfThat, this.ranks);
                    return true;
                }
            }
        }

        this.summation.append(percentile.summation);
        this.count.append(percentile.count);

        this.isCalculated = false;
        return true;
    }

    @Override
    public void calculate() {
        if (!isCalculated) {
            final Set<String> keys = summation.keys();
            for (String key : keys) {
                long value = 0;
                if (count.get(key) != 0) {
                    value = summation.get(key) / count.get(key);
                    if (value == 0L && summation.get(key) > 0L) {
                        value = 1;
                    }
                }
                dataset.put(key, value);
            }
            dataset.keys().stream()
                   .map(key -> {
                       if (key.contains(":")) {
                           int index = key.lastIndexOf(":");
                           return Tuple.of(key.substring(0, index), key);
                       } else {
                           return Tuple.of(DEFAULT_GROUP, key);
                       }
                   })
                   .collect(groupingBy(Tuple2::_1, mapping(Tuple2::_2, Collector.of(
                       DataTable::new,
                       (dt, key) -> {
                           String v;
                           if (key.contains(":")) {
                               int index = key.lastIndexOf(":");
                               v = key.substring(index + 1);
                           } else {
                               v = key;
                           }
                           dt.put(v, dataset.get(key));
                       },
                       DataTable::append
                   ))))
                   .forEach((group, subDataset) -> {
                       long total;
                       total = subDataset.sumOfValues();

                    int[] roofs = new int[ranks.size()];
                    for (int i = 0; i < ranks.size(); i++) {
                        roofs[i] = Math.round(total * ranks.get(i) * 1.0f / 100);
                    }

                    int count = 0;
                    final List<String> sortedKeys = subDataset.sortedKeys(Comparator.comparingLong(Long::parseLong));

                    int loopIndex = 0;

                    for (String key : sortedKeys) {
                        final Long value = subDataset.get(key);

                        count += value;
                        for (int rankIdx = loopIndex; rankIdx < roofs.length; rankIdx++) {
                            int roof = roofs[rankIdx];

                            if (count >= roof) {
                                if (group.equals(DEFAULT_GROUP)) {
                                    percentileValues.put(String.valueOf(ranks.get(rankIdx)), Long.parseLong(key));
                                } else {
                                    percentileValues.put(String.format("%s:%s", group, ranks.get(rankIdx)), Long.parseLong(key));
                                }
                                loopIndex++;
                            } else {
                                break;
                            }
                        }
                    }
                });
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
    protected String id0() {
        return getTimeBucket() + Const.ID_CONNECTOR + entityId;
    }

    @Override
    public Class<? extends AvgPercentileFunctionBuilder> builder() {
        return AvgPercentileFunctionBuilder.class;
    }

    public static class AvgPercentileFunctionBuilder implements StorageHashMapBuilder<AvgHistogramPercentileFunction> {

        @Override
        public AvgHistogramPercentileFunction storage2Entity(final Map<String, Object> dbMap) {
            AvgHistogramPercentileFunction metrics = new AvgHistogramPercentileFunction() {
                @Override
                public AcceptableValue<PercentileArgument> createNew() {
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
        public Map<String, Object> entity2Storage(final AvgHistogramPercentileFunction storageData) {
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
        if (this == o) {
            return true;
        }
        if (!(o instanceof AvgHistogramPercentileFunction)) {
            return false;
        }
        AvgHistogramPercentileFunction function = (AvgHistogramPercentileFunction) o;
        return Objects.equals(entityId, function.entityId) &&
            getTimeBucket() == function.getTimeBucket();
    }

    @Override
    public int hashCode() {
        return Objects.hash(entityId, getTimeBucket());
    }
}
