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

import org.apache.skywalking.oap.server.core.analysis.Stream;
import org.apache.skywalking.oap.server.core.analysis.metrics.annotation.ConstOne;
import org.apache.skywalking.oap.server.core.analysis.metrics.annotation.Entrance;
import org.apache.skywalking.oap.server.core.analysis.metrics.annotation.MetricsFunction;

/**
 * A function whose constructor reads the generated class's @Stream, which names the builder generated after it.
 */
@MetricsFunction(functionName = "chkStreamName")
public abstract class ChecksStreamNameMetrics extends Metrics {
    private final String streamName;

    protected ChecksStreamNameMetrics() {
        final Stream stream = getClass().getAnnotation(Stream.class);
        this.streamName = stream == null ? null : stream.name();
    }

    public String getStreamName() {
        return streamName;
    }

    @Entrance
    public final void combine(@ConstOne long count) {
    }

    @Override
    public boolean combine(Metrics metrics) {
        return true;
    }

    @Override
    public void calculate() {
    }
}
