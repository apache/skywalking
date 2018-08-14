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

import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.server.core.analysis.indicator.annotation.Entrance;
import org.apache.skywalking.oap.server.core.analysis.indicator.annotation.Expression;
import org.apache.skywalking.oap.server.core.analysis.indicator.annotation.ExpressionArg0;
import org.apache.skywalking.oap.server.core.analysis.indicator.annotation.ExpressionArg1;
import org.apache.skywalking.oap.server.core.analysis.indicator.annotation.IndicatorOperator;
import org.apache.skywalking.oap.server.core.analysis.indicator.expression.BooleanBinaryMatch;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;

/**
 * @author wusheng
 */
@IndicatorOperator
public abstract class PercentIndicator extends Indicator {
    protected static final String TOTAL = "total";
    protected static final String MATCH = "match";
    protected static final String PERCENTAGE = "percentage";

    @Getter @Setter @Column(columnName = TOTAL) private long total;
    @Getter @Setter @Column(columnName = PERCENTAGE) private int percentage;
    @Getter @Setter @Column(columnName = MATCH) private long match;

    @Entrance
    public final void combine(@Expression BooleanBinaryMatch expression, @ExpressionArg0 Object leftValue,
        @ExpressionArg1 Object rightValue) {
        expression.setLeft(leftValue);
        expression.setRight(rightValue);
        if (expression.match()) {
            match++;
        }
        total++;
    }

    @Override public final void combine(Indicator indicator) {
        total += ((PercentIndicator)indicator).total;
        match += ((PercentIndicator)indicator).match;
    }
}
