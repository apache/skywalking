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
 *
 * @author wusheng
 */
@MetricsFunction(functionName = "percentile")
public abstract class PercentileMetrics extends GroupMetrics implements MultiIntValuesHolder {
    protected static final String DATASET = "dataset";
    protected static final String VALUE = "value";
    protected static final String PRECISION = "precision";

    private static final int[] RANKS = {50, 75, 90, 95, 99};

    @Getter @Setter @Column(columnName = VALUE, isValue = true) private IntKeyLongValueHashMap percentileValues;
    @Getter @Setter @Column(columnName = PRECISION) private int precision;
    @Getter @Setter @Column(columnName = DATASET) private IntKeyLongValueHashMap dataset;

    private boolean isCalculated;

    public PercentileMetrics() {
        percentileValues = new IntKeyLongValueHashMap(RANKS.length);
        dataset = new IntKeyLongValueHashMap(30);
    }

    @Entrance
    public final void combine(@SourceFrom int value, @Arg int precision) {
        this.isCalculated = false;
        this.precision = precision;

        int index = value / precision;
        IntKeyLongValue element = dataset.get(index);
        if (element == null) {
            element = new IntKeyLongValue(index, 1);
            dataset.put(element.getKey(), element);
        } else {
            element.addValue(1);
        }
    }

    @Override
    public void combine(Metrics metrics) {
        this.isCalculated = false;

        PercentileMetrics percentileMetrics = (PercentileMetrics)metrics;
        combine(percentileMetrics.getDataset(), this.dataset);
    }

    @Override
    public final void calculate() {

        if (!isCalculated) {
            int total = dataset.values().stream().mapToInt(element -> (int)element.getValue()).sum();

            int index = 0;
            int[] roofs = new int[RANKS.length];
            for (int i = 0; i < RANKS.length; i++) {
                roofs[i] = Math.round(total * RANKS[i] * 1.0f / 100);
            }

            int count = 0;
            IntKeyLongValue[] sortedData = dataset.values().stream().sorted(new Comparator<IntKeyLongValue>() {
                @Override public int compare(IntKeyLongValue o1, IntKeyLongValue o2) {
                    return o1.getKey() - o2.getKey();
                }
            }).toArray(IntKeyLongValue[]::new);
            for (IntKeyLongValue element : sortedData) {
                count += element.getValue();
                for (int i = index; i < roofs.length; i++) {
                    int roof = roofs[i];

                    if (count >= roof) {
                        percentileValues.put(index, new IntKeyLongValue(index, element.getKey() * precision));
                        index++;
                    } else {
                        break;
                    }
                }
            }
        }
    }

    public int[] getValues() {
        int[] values = new int[percentileValues.size()];
        for (int i = 0; i < values.length; i++) {
            values[i] = (int)percentileValues.get(i).getValue();
        }
        return values;
    }
}
