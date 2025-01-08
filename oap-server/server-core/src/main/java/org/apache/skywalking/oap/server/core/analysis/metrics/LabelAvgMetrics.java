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

import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.server.core.analysis.metrics.annotation.Arg;
import org.apache.skywalking.oap.server.core.analysis.metrics.annotation.DefaultValue;
import org.apache.skywalking.oap.server.core.analysis.metrics.annotation.Entrance;
import org.apache.skywalking.oap.server.core.analysis.metrics.annotation.MetricsFunction;
import org.apache.skywalking.oap.server.core.storage.annotation.BanyanDB;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;
import org.apache.skywalking.oap.server.core.storage.annotation.ElasticSearch;

import java.util.Objects;
import java.util.Set;

@MetricsFunction(functionName = "labelAvg")
public abstract class LabelAvgMetrics extends Metrics implements LabeledValueHolder {
    protected static final String SUMMATION = "datatable_summation";
    protected static final String COUNT = "datatable_count";
    protected static final String VALUE = "datatable_value";

    protected static final String LABEL_NAME = "n";

    @Getter
    @Setter
    @Column(name = SUMMATION, storageOnly = true)
    @ElasticSearch.Column(legacyName = "summation")
    @BanyanDB.MeasureField
    protected DataTable summation;
    @Getter
    @Setter
    @Column(name = COUNT, storageOnly = true)
    @ElasticSearch.Column(legacyName = "count")
    @BanyanDB.MeasureField
    protected DataTable count;
    @Getter
    @Setter
    @Column(name = VALUE, dataType = Column.ValueDataType.LABELED_VALUE, storageOnly = true)
    @ElasticSearch.Column(legacyName = "value")
    @BanyanDB.MeasureField
    private DataTable value;

    private boolean isCalculated;
    private int maxLabelCount;

    public LabelAvgMetrics() {
        this.summation = new DataTable(30);
        this.count = new DataTable(30);
        this.value = new DataTable(30);
    }

    @Entrance
    public final void combine(@Arg String label, @Arg long count, @DefaultValue("1024") int maxLabelCount) {
        this.isCalculated = false;
        this.maxLabelCount = maxLabelCount;
        this.summation.valueAccumulation(label, count, maxLabelCount);
        this.count.valueAccumulation(label, 1L, maxLabelCount);
    }

    @Override
    public boolean combine(Metrics metrics) {
        this.isCalculated = false;
        final LabelAvgMetrics labelCountMetrics = (LabelAvgMetrics) metrics;
        this.summation.append(labelCountMetrics.summation, labelCountMetrics.maxLabelCount);
        this.count.append(labelCountMetrics.count, labelCountMetrics.maxLabelCount);
        return true;
    }

    @Override
    public void calculate() {
        if (isCalculated) {
            return;
        }

        Set<String> keys = count.keys();
        for (String key : keys) {
            Long s = summation.get(key);
            if (Objects.isNull(s)) {
                continue;
            }
            Long c = count.get(key);
            if (Objects.isNull(c)) {
                continue;
            }
            long result = s / c;
            if (result == 0 && s > 0) {
                result = 1;
            }
            final DataLabel label = new DataLabel();
            label.put(LABEL_NAME, key);
            value.put(label, result);
        }
    }

    @Override
    public DataTable getValue() {
        return this.value;
    }
}
