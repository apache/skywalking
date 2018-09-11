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

package org.apache.skywalking.oap.server.core.analysis.indicator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.server.core.analysis.indicator.annotation.Arg;
import org.apache.skywalking.oap.server.core.analysis.indicator.annotation.Entrance;
import org.apache.skywalking.oap.server.core.analysis.indicator.annotation.IndicatorOperator;
import org.apache.skywalking.oap.server.core.analysis.indicator.annotation.SourceFrom;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;

/**
 * Thermodynamic indicator represents the calculator for heat map.
 *
 * It groups the given collection of values by the given step and number of steps.
 *
 * A heat map (or heatmap) is a graphical representation of data where the individual values contained in a matrix are
 * represented as colors.
 *
 * @author wusheng
 */
@IndicatorOperator
public abstract class ThermodynamicIndicator extends Indicator {
    protected static final String DETAIL_GROUP = "detail_group";
    protected static final String STEP = "step";
    protected static final String NUM_OF_STEPS = "num_of_steps";

    @Getter @Setter @Column(columnName = STEP) private int step = 0;
    @Getter @Setter @Column(columnName = NUM_OF_STEPS) private int numOfSteps = 0;
    @Getter @Setter @Column(columnName = DETAIL_GROUP) private List<IntKeyLongValue> detailGroup = new ArrayList<>(30);

    private Map<Integer, IntKeyLongValue> detailIndex;

    /**
     * @param value
     * @param step the size of each step. A positive integer.
     * @param maxNumOfSteps Steps are used to group incoming value.
     *
     * Data will be grouped in <br/>
     *
     * [0, step), [step, step * 2), ..., [step * (maxNumOfSteps - 1), step * maxNumOfSteps), [step * maxNumOfSteps,
     * MAX)
     */
    @Entrance
    public final void combine(@SourceFrom int value, @Arg int step, @Arg int maxNumOfSteps) {
        if (this.step == 0) {
            this.step = step;
        }
        if (this.numOfSteps == 0) {
            this.numOfSteps = maxNumOfSteps + 1;
        }

        indexCheckAndInit();

        int index = value / step;
        if (index > maxNumOfSteps) {
            index = numOfSteps;
        }
        IntKeyLongValue element = detailIndex.get(index);
        if (element == null) {
            element = new IntKeyLongValue();
            element.setKey(index);
            element.setValue(1);
            addElement(element);
        } else {
            element.addValue(1);
        }
    }

    @Override
    public void combine(Indicator indicator) {
        ThermodynamicIndicator thermodynamicIndicator = (ThermodynamicIndicator)indicator;
        this.indexCheckAndInit();
        thermodynamicIndicator.indexCheckAndInit();

        thermodynamicIndicator.detailIndex.forEach((key, element) -> {
            IntKeyLongValue existingElement = this.detailIndex.get(key);
            if (existingElement == null) {
                existingElement = new IntKeyLongValue();
                existingElement.setKey(key);
                existingElement.setValue(element.getValue());
                addElement(element);
            } else {
                existingElement.addValue(element.getValue());
            }
        });
    }

    /**
     * For Thermodynamic indicator, no single value field. Need to do nothing here.
     */
    @Override
    public final void calculate() {

    }

    private void addElement(IntKeyLongValue element) {
        detailGroup.add(element);
        detailIndex.put(element.getKey(), element);
    }

    private void indexCheckAndInit() {
        if (detailIndex == null) {
            detailIndex = new HashMap<>();
            detailGroup.forEach(element -> detailIndex.put(element.getKey(), element));
        }
    }
}
