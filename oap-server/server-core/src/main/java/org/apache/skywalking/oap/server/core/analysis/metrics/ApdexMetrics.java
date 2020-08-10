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
import org.apache.skywalking.oap.server.core.analysis.ConfigurationDictionary;
import org.apache.skywalking.oap.server.core.analysis.metrics.annotation.Arg;
import org.apache.skywalking.oap.server.core.analysis.metrics.annotation.Entrance;
import org.apache.skywalking.oap.server.core.analysis.metrics.annotation.MetricsFunction;
import org.apache.skywalking.oap.server.core.analysis.metrics.annotation.SourceFrom;
import org.apache.skywalking.oap.server.core.query.sql.Function;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;

/**
 * Apdex dissatisfaction levels of Tolerating (apdex_t) and Frustrated (apdex_f) indicate how slow site performance
 * contributes to poor customer experiences in your app. For example:
 * <p>
 * 10000: All responses are satisfactory. Tolerating responses half satisfy a user. For example, if all responses are
 * Tolerating, then the Apdex value will be 5000. 0: None of the responses are satisfactory.
 */
@MetricsFunction(functionName = "apdex")
public abstract class ApdexMetrics extends Metrics implements IntValueHolder {
    @Setter
    private static ConfigurationDictionary DICT;
    protected static final String TOTAL_NUM = "total_num";
    // Level: satisfied
    protected static final String S_NUM = "s_num";
    // Level: tolerated
    protected static final String T_NUM = "t_num";
    protected static final String VALUE = "value";

    @Getter
    @Setter
    @Column(columnName = TOTAL_NUM, storageOnly = true)
    private long totalNum;
    @Getter
    @Setter
    @Column(columnName = S_NUM, storageOnly = true)
    private long sNum;
    @Getter
    @Setter
    @Column(columnName = T_NUM, storageOnly = true)
    private long tNum;
    @Getter
    @Setter
    @Column(columnName = VALUE, dataType = Column.ValueDataType.COMMON_VALUE, function = Function.Avg)
    private int value;

    @Entrance
    public final void combine(@SourceFrom int value, @Arg String name, @Arg boolean status) {
        int t = DICT.lookup(name).intValue();
        int t4 = t * 4;
        totalNum++;
        if (!status || value >= t4) {
            return;
        }
        if (value >= t) {
            tNum++;
        } else {
            sNum++;
        }
    }

    @Override
    public final void combine(Metrics metrics) {
        tNum += ((ApdexMetrics) metrics).tNum;
        sNum += ((ApdexMetrics) metrics).sNum;
        totalNum += ((ApdexMetrics) metrics).totalNum;
    }

    @Override
    public void calculate() {
        value = (int) ((sNum * 10000 + tNum * 10000 / 2) / totalNum);
    }

    @Override
    public int getValue() {
        return value;
    }
}
