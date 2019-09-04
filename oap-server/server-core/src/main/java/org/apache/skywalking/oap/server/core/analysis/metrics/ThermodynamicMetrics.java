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

import lombok.*;
import org.apache.skywalking.oap.server.core.analysis.metrics.annotation.*;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;

/**
 * Thermodynamic metrics represents the calculator for heat map.
 *
 * It groups the given collection of values by the given step and number of steps.
 *
 * A heat map (or heatmap) is a graphical representation of data where the individual values contained in a matrix are
 * represented as colors.
 *
 * @author wusheng, peng-yongsheng
 */
@MetricsFunction(functionName = "thermodynamic")
public abstract class ThermodynamicMetrics extends GroupMetrics {

    public static final String DETAIL_GROUP = "detail_group";
    public static final String STEP = "step";
    public static final String NUM_OF_STEPS = "num_of_steps";

    @Getter @Setter @Column(columnName = STEP) private int step = 0;
    @Getter @Setter @Column(columnName = NUM_OF_STEPS) private int numOfSteps = 0;
    @Getter @Setter @Column(columnName = DETAIL_GROUP, isValue = true) private IntKeyLongValueHashMap detailGroup = new IntKeyLongValueHashMap(30);

    /**
     * Data will be grouped in
     *
     * [0, step), [step, step * 2), ..., [step * (maxNumOfSteps - 1), step * maxNumOfSteps), [step * maxNumOfSteps,
     * MAX)
     *
     * @param value
     * @param step the size of each step. A positive integer.
     * @param maxNumOfSteps Steps are used to group incoming value.
     */
    @Entrance
    public final void combine(@SourceFrom int value, @Arg int step, @Arg int maxNumOfSteps) {
        if (this.step == 0) {
            this.step = step;
        }
        if (this.numOfSteps == 0) {
            this.numOfSteps = maxNumOfSteps;
        }

        int index = value / step;
        if (index > maxNumOfSteps) {
            index = numOfSteps;
        }

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
        ThermodynamicMetrics thermodynamicMetrics = (ThermodynamicMetrics)metrics;
        combine(thermodynamicMetrics.getDetailGroup(), this.detailGroup);
    }

    /**
     * For Thermodynamic metrics, no single value field. Need to do nothing here.
     */
    @Override
    public final void calculate() {
    }
}
