/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.skywalking.oap.server.core.analysis.metrics;

import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.server.core.analysis.metrics.annotation.Entrance;
import org.apache.skywalking.oap.server.core.analysis.metrics.annotation.Expression;
import org.apache.skywalking.oap.server.core.analysis.metrics.annotation.MetricsFunction;
import org.apache.skywalking.oap.server.core.query.sql.Function;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;

@MetricsFunction(functionName = "rate")
public abstract class RateMetrics extends Metrics implements IntValueHolder {
    protected static final String DENOMINATOR = "denominator";
    protected static final String NUMERATOR = "numerator";
    protected static final String PERCENTAGE = "percentage";

    @Getter
    @Setter
    @Column(columnName = DENOMINATOR)
    private long denominator;
    @Getter
    @Setter
    @Column(columnName = PERCENTAGE, dataType = Column.ValueDataType.COMMON_VALUE, function = Function.Avg)
    private int percentage;
    @Getter
    @Setter
    @Column(columnName = NUMERATOR)
    private long numerator;

    @Entrance
    public final void combine(@Expression boolean isNumerator, @Expression boolean isDenominator) {
        if (isNumerator) {
            numerator++;
        }
        if (isDenominator) {
            denominator++;
        }
    }

    @Override
    public final boolean combine(Metrics metrics) {
        denominator += ((RateMetrics) metrics).denominator;
        numerator += ((RateMetrics) metrics).numerator;
        return true;
    }

    @Override
    public void calculate() {
        if (denominator == 0) {
            return;
        }
        percentage = (int) (numerator * 10000 / denominator);
    }

    @Override
    public int getValue() {
        return percentage;
    }
}
