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

package org.apache.skywalking.oap.server.core.analysis.metrics;

import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;
import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.server.core.analysis.metrics.annotation.Arg;
import org.apache.skywalking.oap.server.core.analysis.metrics.annotation.Entrance;
import org.apache.skywalking.oap.server.core.analysis.metrics.annotation.MetricsFunction;
import org.apache.skywalking.oap.server.core.analysis.metrics.annotation.SourceFrom;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;

/**
 * Percentile is a better implementation than {@link PxxMetrics}. It is introduced since 7.0.0, it could calculate the
 * multiple P50/75/90/95/99 values once for all.
 */
@MetricsFunction(functionName = "percentile")
public abstract class PercentileMetrics extends Metrics implements MultiIntValuesHolder {
    protected static final String DATASET = "dataset";
    protected static final String VALUE = "value";
    protected static final String PRECISION = "precision";

    private static final int[] RANKS = {
        50,
        75,
        90,
        95,
        99
    };

    @Getter
    @Setter
    @Column(columnName = VALUE, dataType = Column.ValueDataType.LABELED_VALUE, storageOnly = true)
    private DataTable percentileValues;
    @Getter
    @Setter
    @Column(columnName = PRECISION, storageOnly = true)
    private int precision;
    @Getter
    @Setter
    @Column(columnName = DATASET, storageOnly = true)
    private DataTable dataset;

    private boolean isCalculated;

    public PercentileMetrics() {
        percentileValues = new DataTable(RANKS.length);
        dataset = new DataTable(30);
    }

    @Entrance
    public final void combine(@SourceFrom int value, @Arg int precision) {
        this.isCalculated = false;
        this.precision = precision;

        String index = String.valueOf(value / precision);
        dataset.valueAccumulation(index, 1L);
    }

    @Override
    public void combine(Metrics metrics) {
        this.isCalculated = false;

        PercentileMetrics percentileMetrics = (PercentileMetrics) metrics;
        this.dataset.append(percentileMetrics.dataset);
    }

    @Override
    public final void calculate() {
        if (!isCalculated) {
            long total = dataset.sumOfValues();

            int[] roofs = new int[RANKS.length];
            for (int i = 0; i < RANKS.length; i++) {
                roofs[i] = Math.round(total * RANKS[i] * 1.0f / 100);
            }

            int count = 0;
            final List<String> sortedKeys = dataset.sortedKeys(Comparator.comparingInt(Integer::parseInt));

            int loopIndex = 0;
            for (String key : sortedKeys) {
                final Long value = dataset.get(key);

                count += value;
                for (int rankIdx = loopIndex; rankIdx < roofs.length; rankIdx++) {
                    int roof = roofs[rankIdx];

                    if (count >= roof) {
                        percentileValues.put(String.valueOf(rankIdx), Long.parseLong(key) * precision);
                        loopIndex++;
                    } else {
                        break;
                    }
                }
            }
        }
    }

    public int[] getValues() {
        return percentileValues.sortedValues(Comparator.comparingInt(Integer::parseInt))
                               .stream()
                               .flatMapToInt(l -> IntStream.of(l.intValue()))
                               .toArray();
    }
}
