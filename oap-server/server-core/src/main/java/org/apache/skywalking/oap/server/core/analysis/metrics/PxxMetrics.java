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
import org.apache.skywalking.oap.server.core.analysis.metrics.annotation.SourceFrom;
import org.apache.skywalking.oap.server.core.query.sql.Function;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;

/**
 * PxxMetrics is a parent metrics for p99/p95/p90/p75/p50 metrics. P(xx) metrics is also for P(xx) percentile.
 * <p>
 * A percentile (or a centile) is a measure used in statistics indicating the value below which a given percentage of
 * observations in a group of observations fall. For example, the 20th percentile is the value (or score) below which
 * 20% of the observations may be found.
 */
public abstract class PxxMetrics extends Metrics implements IntValueHolder {

    protected static final String DETAIL_GROUP = "detail_group";
    protected static final String VALUE = "value";
    protected static final String PRECISION = "precision";

    @Getter
    @Setter
    @Column(columnName = VALUE, dataType = Column.ValueDataType.HISTOGRAM, function = Function.Avg)
    private int value;
    @Getter
    @Setter
    @Column(columnName = PRECISION, storageOnly = true)
    private int precision;
    @Getter
    @Setter
    @Column(columnName = DETAIL_GROUP, storageOnly = true)
    private DataTable detailGroup;

    private final int percentileRank;
    private boolean isCalculated;

    public PxxMetrics(int percentileRank) {
        this.percentileRank = percentileRank;
        detailGroup = new DataTable(30);
    }

    @Entrance
    public final void combine(@SourceFrom int value, @Arg int precision) {
        this.isCalculated = false;
        this.precision = precision;

        String index = String.valueOf(value / precision);
        Long element = detailGroup.get(index);
        if (element == null) {
            element = 1L;
        } else {
            element++;
        }
        detailGroup.put(index, element);
    }

    @Override
    public boolean combine(Metrics metrics) {
        this.isCalculated = false;

        PxxMetrics pxxMetrics = (PxxMetrics) metrics;
        this.detailGroup.append(pxxMetrics.detailGroup);
        return true;
    }

    @Override
    public final void calculate() {

        if (!isCalculated) {
            long total = detailGroup.sumOfValues();
            int roof = Math.round(total * percentileRank * 1.0f / 100);

            long count = 0;
            final List<String> sortedKeys = detailGroup.sortedKeys(Comparator.comparingInt(Integer::parseInt));

            for (String index : sortedKeys) {
                final Long value = detailGroup.get(index);
                count += value;
                if (count >= roof) {
                    this.value = Integer.parseInt(index) * precision;
                    return;
                }
            }
        }
    }
}
