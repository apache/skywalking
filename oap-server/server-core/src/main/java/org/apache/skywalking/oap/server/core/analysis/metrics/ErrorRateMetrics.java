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
import org.apache.skywalking.oap.server.core.analysis.metrics.annotation.MetricsFunction;
import org.apache.skywalking.oap.server.core.analysis.metrics.annotation.SourceFrom;
import org.apache.skywalking.oap.server.core.browser.source.BrowserAppTrafficCategory;
import org.apache.skywalking.oap.server.core.query.sql.Function;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;

/**
 * Used to calculate error rate, browser related metrics only.
 */
@MetricsFunction(functionName = "er")
public abstract class ErrorRateMetrics extends Metrics implements IntValueHolder {

    protected static final String TOTAL = "total";
    protected static final String MATCH = "match";
    protected static final String VALUE = "value";

    @Setter
    @Getter
    @Column(columnName = TOTAL)
    private long total;

    @Setter
    @Getter
    @Column(columnName = MATCH)
    private long match;

    @Setter
    @Getter
    @Column(columnName = VALUE, dataType = Column.ValueDataType.COMMON_VALUE, function = Function.Avg)
    private int value;

    @Override
    public int getValue() {
        return value;
    }

    @Entrance
    public final void combine(@SourceFrom BrowserAppTrafficCategory category) {
        switch (category) {
            case NORMAL:
                total++;
                break;
            case FIRST_ERROR:
                match++;
                break;
        }
    }

    @Override
    public void combine(final Metrics metrics) {
        total += ((ErrorRateMetrics) metrics).total;
        match += ((ErrorRateMetrics) metrics).match;
    }

    @Override
    public void calculate() {
        if (total > 0) {
            value = (int) (match * 10000 / total);
        }
    }
}
