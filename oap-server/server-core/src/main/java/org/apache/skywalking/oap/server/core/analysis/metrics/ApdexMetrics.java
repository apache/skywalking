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
 * @author gaohongtao
 */
@MetricsFunction(functionName = "apdex")
public abstract class ApdexMetrics extends Metrics implements IntValueHolder {
    @Setter
    private static ConfigurationDictionary DICT;
    protected static final String VALUE = "value";
    protected static final String T = "t";
    protected static final String SCORE = "score";

    @Getter @Setter @Column(columnName = VALUE) private int value;
    @Getter @Setter @Column(columnName = T) private int t;
    @Getter @Setter @Column(columnName = SCORE, isValue = true, function = Function.Avg) private int score;

    @Entrance
    public final void combine(@SourceFrom int value, @Arg String name) {
        this.value += value;
        this.t += DICT.lookup(name).intValue();
    }

    @Override public final void combine(Metrics metrics) {
        t += ((ApdexMetrics)metrics).t;
        value += ((ApdexMetrics)metrics).value;
    }

    @Override public void calculate() {
        score = (int)(value * 10000 / t);
    }

    @Override public int getValue() {
        return score;
    }
}
