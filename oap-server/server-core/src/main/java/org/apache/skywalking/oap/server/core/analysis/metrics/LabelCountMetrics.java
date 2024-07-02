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
import org.apache.skywalking.oap.server.core.analysis.metrics.annotation.ConstOne;
import org.apache.skywalking.oap.server.core.analysis.metrics.annotation.Entrance;
import org.apache.skywalking.oap.server.core.analysis.metrics.annotation.MetricsFunction;
import org.apache.skywalking.oap.server.core.storage.annotation.BanyanDB;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;
import org.apache.skywalking.oap.server.core.storage.annotation.ElasticSearch;

@MetricsFunction(functionName = "labelCount")
public abstract class LabelCountMetrics extends Metrics implements LabeledValueHolder {
    protected static final String DATASET = "dataset";
    protected static final String VALUE = "datatable_value";

    protected static final String LABEL_NAME = "n";

    @Getter
    @Setter
    @Column(name = DATASET, storageOnly = true)
    @BanyanDB.MeasureField
    private DataTable dataset;

    @Getter
    @Setter
    @Column(name = VALUE, dataType = Column.ValueDataType.LABELED_VALUE, storageOnly = true)
    @ElasticSearch.Column(legacyName = "value")
    @BanyanDB.MeasureField
    private DataTable value;

    private boolean isCalculated;

    public LabelCountMetrics() {
        this.dataset = new DataTable(30);
        this.value = new DataTable(30);
    }

    @Entrance
    public final void combine(@Arg String label, @ConstOne long count) {
        this.isCalculated = false;
       this.dataset.valueAccumulation(label, count);
    }

    @Override
    public boolean combine(Metrics metrics) {
        this.isCalculated = false;
        final LabelCountMetrics labelCountMetrics = (LabelCountMetrics) metrics;
        this.dataset.append(labelCountMetrics.dataset);
        return true;
    }

    @Override
    public void calculate() {
        if (isCalculated) {
            return;
        }

        // convert dataset to labeled value
        for (String key : this.dataset.keys()) {
            final DataLabel label = new DataLabel();
            label.put(LABEL_NAME, key);
            this.value.put(label, this.dataset.get(key));
        }
    }

    @Override
    public DataTable getValue() {
        return this.value;
    }
}
