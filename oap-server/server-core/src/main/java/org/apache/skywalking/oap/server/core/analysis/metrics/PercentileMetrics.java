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
    protected static final String DETAIL_GROUP = "detail_group";
    protected static final String VALUE = "value";
    protected static final String PRECISION = "precision";

    private static final int[] RANKS = {50, 75, 90, 95, 99};

    @Getter @Setter @Column(columnName = VALUE) private int[] values = {0, 0, 0, 0, 0};
    @Getter @Setter @Column(columnName = PRECISION) private int precision;
    @Getter @Setter @Column(columnName = DETAIL_GROUP) private IntKeyLongValueHashMap detailGroup;

    private boolean isCalculated;

    public PercentileMetrics() {
        detailGroup = new IntKeyLongValueHashMap(30);
    }

    @Entrance
    public final void combine(@SourceFrom int value, @Arg int precision) {
        this.isCalculated = false;
        this.precision = precision;

        int index = value / precision;
        IntKeyLongValue element = detailGroup.get(index);
        if (element == null) {
            element = new IntKeyLongValue(index, 1);
            detailGroup.put(element.getKey(), element);
        } else {
            element.addValue(1);
        }
    }

    @Override
    public void combine(Metrics metrics) {
        this.isCalculated = false;

        PxxMetrics pxxMetrics = (PxxMetrics)metrics;
        combine(pxxMetrics.getDetailGroup(), this.detailGroup);
    }

    @Override
    public final void calculate() {

        if (!isCalculated) {
            int total = detailGroup.values().stream().mapToInt(element -> (int)element.getValue()).sum();
            for (int i = 0; i < RANKS.length; i++) {
                int percentileRank = RANKS[i];
                int roof = Math.round(total * percentileRank * 1.0f / 100);

                int count = 0;
                IntKeyLongValue[] sortedData = detailGroup.values().stream().sorted(new Comparator<IntKeyLongValue>() {
                    @Override public int compare(IntKeyLongValue o1, IntKeyLongValue o2) {
                        return o1.getKey() - o2.getKey();
                    }
                }).toArray(IntKeyLongValue[]::new);
                for (IntKeyLongValue element : sortedData) {
                    count += element.getValue();
                    if (count >= roof) {
                        values[i] = element.getKey() * precision;
                        return;
                    }
                }
            }
        }
    }
}
