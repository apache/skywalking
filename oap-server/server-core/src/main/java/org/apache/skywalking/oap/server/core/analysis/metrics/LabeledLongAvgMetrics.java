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

import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.server.core.analysis.metrics.annotation.Arg;
import org.apache.skywalking.oap.server.core.analysis.metrics.annotation.ConstOne;
import org.apache.skywalking.oap.server.core.analysis.metrics.annotation.Entrance;
import org.apache.skywalking.oap.server.core.analysis.metrics.annotation.MetricsFunction;
import org.apache.skywalking.oap.server.core.analysis.metrics.annotation.SourceFrom;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;
import org.apache.skywalking.oap.server.core.storage.annotation.ElasticSearch;

@MetricsFunction(functionName = "labeledLongAvg")
public abstract class LabeledLongAvgMetrics extends Metrics implements LabeledValueHolder {

    protected static final String SUMMATION = "summation";
    protected static final String COUNT = "count";
    protected static final String VALUE = "value";

    @Getter
    @Setter
    @Column(columnName = SUMMATION, storageOnly = true)
    @ElasticSearch.Column(columnAlias = "datatable_summation")
    protected DataTable summation = new DataTable(30);

    @Getter
    @Setter
    @Column(columnName = COUNT, storageOnly = true)
    @ElasticSearch.Column(columnAlias = "datatable_count")
    protected DataTable count = new DataTable(30);
    @Getter
    @Setter
    @Column(columnName = VALUE, dataType = Column.ValueDataType.LABELED_VALUE, storageOnly = true)
    @ElasticSearch.Column(columnAlias = "datatable_value")
    private DataTable value = new DataTable(30);

    @Entrance
    public final void combine(@SourceFrom long value, @ConstOne long count, @Arg String[] labels) {
        String label = Arrays.stream(labels).reduce((a, b) -> a + "#" + b).orElse("");
        this.count.put(label, count);
        this.summation.put(label, value);
    }

    @Override
    public final boolean combine(Metrics metrics) {
        if (!(metrics instanceof LabeledLongAvgMetrics)) {
            return false;
        }
        LabeledLongAvgMetrics labeledMetrics = (LabeledLongAvgMetrics) metrics;
        this.summation.append(labeledMetrics.getSummation());
        this.count.append(labeledMetrics.getCount());
        return true;
    }

    @Override
    public final void calculate() {
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
            value.put(key, result);
        }
    }
}
