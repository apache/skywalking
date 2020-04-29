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
import org.apache.skywalking.oap.server.core.analysis.metrics.annotation.Entrance;
import org.apache.skywalking.oap.server.core.analysis.metrics.annotation.MetricsFunction;
import org.apache.skywalking.oap.server.core.analysis.metrics.annotation.SourceFrom;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;

/**
 * Histogram metrics represents the calculator for heat map.
 * <p>
 * It groups the given collection of values by the given step and number of steps.
 * <p>
 * A heat map (or heatmap) is a graphical representation of data where the individual values contained in a matrix are
 * represented as colors.
 */
@MetricsFunction(functionName = "histogram")
public abstract class HistogramMetrics extends Metrics {

    public static final String DATASET = "dataset";

    @Getter
    @Setter
    @Column(columnName = DATASET, dataType = Column.ValueDataType.HISTOGRAM, storageOnly = true, defaultValue = 0)
    private DataTable dataset = new DataTable(30);

    /**
     * Data will be grouped in
     * <pre>
     * key = 0, represents [0, 100), value = count of requests in the latency range.
     * key = 100, represents [100, 200), value = count of requests in the latency range.
     * ...
     * key = step * maxNumOfSteps, represents [step * maxNumOfSteps, MAX)
     * </pre>
     *
     * @param step          the size of each step. A positive integer.
     * @param maxNumOfSteps Steps are used to group incoming value.
     */
    @Entrance
    public final void combine(@SourceFrom int value, @Arg int step, @Arg int maxNumOfSteps) {
        if (!dataset.hasData()) {
            for (int i = 0; i <= maxNumOfSteps; i++) {
                String key = String.valueOf(i * step);
                dataset.put(key, 0L);
            }
        }

        int index = value / step;
        if (index > maxNumOfSteps) {
            index = maxNumOfSteps;
        }
        String idx = String.valueOf(index * step);

        dataset.valueAccumulation(idx, 1L);
    }

    @Override
    public void combine(Metrics metrics) {
        HistogramMetrics histogramMetrics = (HistogramMetrics) metrics;
        this.dataset.append(histogramMetrics.dataset);
    }

    /**
     * For Thermodynamic metrics, no single value field. Need to do nothing here.
     */
    @Override
    public final void calculate() {
    }
}
