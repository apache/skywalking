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

import org.apache.skywalking.oap.server.core.analysis.metrics.annotation.Arg;
import org.apache.skywalking.oap.server.core.analysis.metrics.annotation.Entrance;
import org.apache.skywalking.oap.server.core.analysis.metrics.annotation.MetricsFunction;

/**
 * A function taking a long argument, such as a file mode written in octal.
 */
@MetricsFunction(functionName = "chkMode")
public abstract class ChecksModeMetrics extends Metrics {
    private long mode;

    @Entrance
    public final void combine(@Arg long mode) {
        this.mode = mode;
    }

    public long getMode() {
        return mode;
    }

    @Override
    public boolean combine(Metrics metrics) {
        return true;
    }

    @Override
    public void calculate() {
    }
}
