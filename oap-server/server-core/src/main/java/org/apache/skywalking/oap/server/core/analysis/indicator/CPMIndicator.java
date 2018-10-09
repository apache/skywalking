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
import org.apache.skywalking.oap.server.core.analysis.indicator.annotation.ConstOne;
import org.apache.skywalking.oap.server.core.analysis.indicator.annotation.Entrance;
import org.apache.skywalking.oap.server.core.analysis.indicator.annotation.IndicatorOperator;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;

/**
 * @author wusheng
 */
@IndicatorOperator
public abstract class CPMIndicator extends Indicator implements LongValueHolder {

    protected static final String VALUE = "value";
    protected static final String TOTAL = "total";

    @Getter @Setter @Column(columnName = VALUE, isValue = true) private long value;
    @Getter @Setter @Column(columnName = TOTAL) private long total;

    @Entrance
    public final void combine(@ConstOne long count) {
        this.total += count;
    }

    @Override public final void combine(Indicator indicator) {
        CPMIndicator countIndicator = (CPMIndicator)indicator;
        combine(countIndicator.total);
    }

    @Override public void calculate() {
        this.value = total / getDurationInMinute();
    }

    @Override public long getValue() {
        return value;
    }
}

