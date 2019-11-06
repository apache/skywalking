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
import lombok.*;
import org.apache.skywalking.oap.server.core.analysis.metrics.annotation.*;
import org.apache.skywalking.oap.server.core.query.sql.Function;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;

/**
 * PxxMetrics is a parent metrics for p99/p95/p90/p75/p50 metrics. P(xx) metrics is also for P(xx) percentile.
 *
 * A percentile (or a centile) is a measure used in statistics indicating the value below which a given percentage of
 * observations in a group of observations fall. For example, the 20th percentile is the value (or score) below which
 * 20% of the observations may be found.
 *
 * @author wusheng, peng-yongsheng
 */
public abstract class PxxMetrics extends GroupMetrics implements IntValueHolder {

    protected static final String DETAIL_GROUP = "detail_group";
    protected static final String VALUE = "value";
    protected static final String PRECISION = "precision";

    @Getter @Setter @Column(columnName = VALUE, isValue = true, function = Function.Avg) private int value;
    @Getter @Setter @Column(columnName = PRECISION) private int precision;
    @Getter @Setter @Column(columnName = DETAIL_GROUP) private IntKeyLongValueHashMap detailGroup;

    private final int percentileRank;
    private boolean isCalculated;

    public PxxMetrics(int percentileRank) {
        this.percentileRank = percentileRank;
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
                    value = element.getKey() * precision;
                    return;
                }
            }
        }
    }
}
