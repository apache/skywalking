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
import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.server.core.analysis.metrics.annotation.Arg;
import org.apache.skywalking.oap.server.core.analysis.metrics.annotation.Entrance;
import org.apache.skywalking.oap.server.core.analysis.metrics.annotation.MetricsFunction;
import org.apache.skywalking.oap.server.core.analysis.metrics.annotation.SourceFrom;
import org.apache.skywalking.oap.server.core.storage.annotation.BanyanDB;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;
import org.apache.skywalking.oap.server.core.storage.annotation.ElasticSearch;

import static org.apache.skywalking.oap.server.core.analysis.metrics.DataLabel.PERCENTILE_LABEL_NAME;

/**
 * Replace the deprecated {@link PercentileMetrics}.
 * Calculate the multiple P50/75/90/95/99 values once for all
 * and store the result in the format: `{p=50},value1|{p=75},value2|{p=90},value3|{p=95},value4|{p=99},value5`.
 *
 * @since 10.0.0
 */
@MetricsFunction(functionName = "percentile2")
public abstract class PercentileMetrics2 extends Metrics implements LabeledValueHolder {
    protected static final String DATASET = "dataset";
    protected static final String VALUE = "datatable_value";
    protected static final String PRECISION = "precision";

    public static final int[] RANKS = {
        50,
        75,
        90,
        95,
        99
    };

    @Getter
    @Setter
    @Column(name = VALUE, dataType = Column.ValueDataType.LABELED_VALUE, storageOnly = true)
    @ElasticSearch.Column(legacyName = "value")
    @BanyanDB.MeasureField
    private DataTable percentileValues;
    @Getter
    @Setter
    @Column(name = PRECISION, storageOnly = true)
    @BanyanDB.MeasureField
    private int precision;
    @Getter
    @Setter
    @Column(name = DATASET, storageOnly = true)
    @BanyanDB.MeasureField
    private DataTable dataset;

    private boolean isCalculated;

    public PercentileMetrics2() {
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
    public boolean combine(Metrics metrics) {
        this.isCalculated = false;

        PercentileMetrics2 percentileMetrics = (PercentileMetrics2) metrics;
        this.dataset.append(percentileMetrics.dataset);
        return true;
    }

    @Override
    public final void calculate() {
        if (!isCalculated) {
            long total = dataset.sumOfValues();

            int[] roofs = new int[RANKS.length];
            for (int i = 0; i < RANKS.length; i++) {
                roofs[i] = Math.round(total * RANKS[i] * 1.0f / 100);
            }

            long count = 0;
            final List<String> sortedKeys = dataset.sortedKeys(Comparator.comparingInt(Integer::parseInt));

            int loopIndex = 0;
            for (String key : sortedKeys) {
                final Long value = dataset.get(key);

                count += value;
                for (int rankIdx = loopIndex; rankIdx < roofs.length; rankIdx++) {
                    int roof = roofs[rankIdx];

                    if (count >= roof) {
                        DataLabel label = new DataLabel();
                        label.put(PERCENTILE_LABEL_NAME, String.valueOf(RANKS[rankIdx]));
                        percentileValues.put(label.toString(), Long.parseLong(key) * precision);
                        loopIndex++;
                    } else {
                        break;
                    }
                }
            }
        }
    }

    @Override
    public DataTable getValue() {
        return percentileValues;
    }
}
