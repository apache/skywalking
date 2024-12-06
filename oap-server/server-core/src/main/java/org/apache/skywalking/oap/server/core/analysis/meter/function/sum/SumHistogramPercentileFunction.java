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

import org.apache.skywalking.oap.server.core.UnexpectedException;
import org.apache.skywalking.oap.server.core.analysis.meter.Meter;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterEntity;
import org.apache.skywalking.oap.server.core.analysis.meter.function.AcceptableValue;
import org.apache.skywalking.oap.server.core.analysis.meter.function.MeterFunction;
import org.apache.skywalking.oap.server.core.analysis.meter.function.PercentileArgument;
import org.apache.skywalking.oap.server.core.analysis.metrics.DataLabel;
import org.apache.skywalking.oap.server.core.analysis.metrics.DataTable;
import org.apache.skywalking.oap.server.core.analysis.metrics.IntList;
import org.apache.skywalking.oap.server.core.analysis.metrics.LabeledValueHolder;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.query.type.Bucket;
import org.apache.skywalking.oap.server.core.remote.grpc.proto.RemoteData;
import org.apache.skywalking.oap.server.core.storage.StorageID;
import org.apache.skywalking.oap.server.core.storage.annotation.BanyanDB;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;
import org.apache.skywalking.oap.server.core.storage.annotation.ElasticSearch;
import org.apache.skywalking.oap.server.core.storage.type.Convert2Entity;
import org.apache.skywalking.oap.server.core.storage.type.Convert2Storage;
import org.apache.skywalking.oap.server.core.storage.type.StorageBuilder;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collector;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static org.apache.skywalking.oap.server.core.analysis.metrics.DataLabel.PERCENTILE_LABEL_NAME;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;

/**
 * SumPercentile intends to calculate percentile based on the summary of raw values over the interval(minute, hour or day).
 */
@MeterFunction(functionName = "sumHistogramPercentile")
@Slf4j
public abstract class SumHistogramPercentileFunction extends Meter implements AcceptableValue<PercentileArgument>, LabeledValueHolder {
    public static final String DATASET = "dataset";
    public static final String RANKS = "ranks";
    public static final String VALUE = "datatable_value";
    protected static final String SUMMATION = "datatable_summation";

    @Setter
    @Getter
    @ElasticSearch.EnableDocValues
    @Column(name = ENTITY_ID)
    @BanyanDB.SeriesID(index = 0)
    private String entityId;
    @Getter
    @Setter
    @Column(name = VALUE, dataType = Column.ValueDataType.LABELED_VALUE, storageOnly = true)
    @ElasticSearch.Column(legacyName = "value")
    @BanyanDB.MeasureField
    private DataTable percentileValues = new DataTable(10);
    @Getter
    @Setter
    @Column(name = SUMMATION, storageOnly = true)
    @ElasticSearch.Column(legacyName = "summation")
    @BanyanDB.MeasureField
    protected DataTable summation = new DataTable(30);
    /**
     * Rank
     */
    @Getter
    @Setter
    @Column(name = RANKS, storageOnly = true)
    @BanyanDB.MeasureField
    private IntList ranks = new IntList(10);

    private boolean isCalculated = false;

    @Override
    public void accept(final MeterEntity entity, final PercentileArgument value) {
        if (summation.size() > 0) {
            if (!value.getBucketedValues().isCompatible(summation)) {
                throw new IllegalArgumentException(
                    "Incompatible BucketedValues [" + value + "] for current PercentileFunction[" + summation + "]");
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
        if (CollectionUtils.isNotEmpty(value.getBucketedValues().getLabels())) {
            template  = value.getBucketedValues().getLabels() + ":%s";
        }
        final long[] values = value.getBucketedValues().getValues();
        for (int i = 0; i < values.length; i++) {
            long bucket = value.getBucketedValues().getBuckets()[i];
            String bucketName = bucket == Long.MIN_VALUE ? Bucket.INFINITE_NEGATIVE : String.valueOf(bucket);
            String key = String.format(template, bucketName);
            summation.valueAccumulation(key, values[i]);
        }

        this.isCalculated = false;
    }

    @Override
    public boolean combine(final Metrics metrics) {
        SumHistogramPercentileFunction percentile = (SumHistogramPercentileFunction) metrics;

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

        this.isCalculated = false;
        return true;
    }

    @Override
    public void calculate() {
        if (!isCalculated) {
            summation.keys().stream()
                   .map(key -> {
                       DataLabel dataLabel = new DataLabel();
                       if (key.contains(":")) {
                           int index = key.lastIndexOf(":");
                           dataLabel.put(key.substring(0, index));
                           return Tuple.of(dataLabel, key);
                       } else {
                           return Tuple.of(dataLabel, key);
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
                           dt.put(v, summation.get(key));
                       },
                       DataTable::append
                   ))))
                   .forEach((labels, subDataset) -> {
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
                                if (labels.isEmpty()) {
                                    labels.put(PERCENTILE_LABEL_NAME, String.valueOf(ranks.get(rankIdx)));
                                    percentileValues.put(labels, Long.parseLong(key));
                                } else {
                                    labels.put(PERCENTILE_LABEL_NAME, String.valueOf(ranks.get(rankIdx)));
                                    percentileValues.put(labels, Long.parseLong(key));
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
        SumHistogramPercentileFunction metrics = (SumHistogramPercentileFunction) createNew();
        metrics.setEntityId(getEntityId());
        metrics.setTimeBucket(toTimeBucketInHour());
        metrics.getSummation().copyFrom(getSummation());
        metrics.getRanks().copyFrom(getRanks());
        metrics.getPercentileValues().copyFrom(getPercentileValues());
        return metrics;
    }

    @Override
    public Metrics toDay() {
        SumHistogramPercentileFunction metrics = (SumHistogramPercentileFunction) createNew();
        metrics.setEntityId(getEntityId());
        metrics.setTimeBucket(toTimeBucketInDay());
        metrics.getSummation().copyFrom(getSummation());
        metrics.getRanks().copyFrom(getRanks());
        metrics.getPercentileValues().copyFrom(getPercentileValues());
        return metrics;
    }

    @Override
    public DataTable getValue() {
        return percentileValues;
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
        this.setRanks(new IntList(remoteData.getDataObjectStrings(1)));
        this.setPercentileValues(new DataTable(remoteData.getDataObjectStrings(2)));
    }

    @Override
    public RemoteData.Builder serialize() {
        RemoteData.Builder remoteBuilder = RemoteData.newBuilder();
        remoteBuilder.addDataLongs(getTimeBucket());

        remoteBuilder.addDataStrings(entityId);

        remoteBuilder.addDataObjectStrings(summation.toStorageData());
        remoteBuilder.addDataObjectStrings(ranks.toStorageData());
        remoteBuilder.addDataObjectStrings(percentileValues.toStorageData());

        return remoteBuilder;
    }

    @Override
    protected StorageID id0() {
        return new StorageID()
            .append(TIME_BUCKET, getTimeBucket())
            .append(ENTITY_ID, getEntityId());
    }

    @Override
    public Class<? extends AvgPercentileFunctionBuilder> builder() {
        return AvgPercentileFunctionBuilder.class;
    }

    public static class AvgPercentileFunctionBuilder implements StorageBuilder<SumHistogramPercentileFunction> {
        @Override
        public SumHistogramPercentileFunction storage2Entity(final Convert2Entity converter) {
            SumHistogramPercentileFunction metrics = new SumHistogramPercentileFunction() {
                @Override
                public AcceptableValue<PercentileArgument> createNew() {
                    throw new UnexpectedException("createNew should not be called");
                }
            };
            metrics.setSummation(new DataTable((String) converter.get(SUMMATION)));
            metrics.setRanks(new IntList((String) converter.get(RANKS)));
            metrics.setPercentileValues(new DataTable((String) converter.get(VALUE)));
            metrics.setTimeBucket(((Number) converter.get(TIME_BUCKET)).longValue());
            metrics.setEntityId((String) converter.get(ENTITY_ID));
            return metrics;
        }

        @Override
        public void entity2Storage(final SumHistogramPercentileFunction storageData, final Convert2Storage converter) {
            converter.accept(SUMMATION, storageData.getSummation());
            converter.accept(RANKS, storageData.getRanks());
            converter.accept(VALUE, storageData.getPercentileValues());
            converter.accept(TIME_BUCKET, storageData.getTimeBucket());
            converter.accept(ENTITY_ID, storageData.getEntityId());
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SumHistogramPercentileFunction)) {
            return false;
        }
        SumHistogramPercentileFunction function = (SumHistogramPercentileFunction) o;
        return Objects.equals(entityId, function.entityId) &&
            getTimeBucket() == function.getTimeBucket();
    }

    @Override
    public int hashCode() {
        return Objects.hash(entityId, getTimeBucket());
    }
}
