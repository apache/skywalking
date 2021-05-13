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

package org.apache.skywalking.oap.server.telemetry.api;

import java.io.Closeable;

/**
 * A histogram samples observations (usually things like request durations or response sizes) and counts them in
 * configurable buckets. It also provides a sum of all observed values.
 */
public abstract class HistogramMetrics {
    public Timer createTimer() {
        return new Timer(this);
    }

    /**
     * Observe an execution, get a duration in second.
     *
     * @param value duration in second.
     */
    public abstract void observe(double value);

    public class Timer implements Closeable {
        private final HistogramMetrics metrics;
        private final long startNanos;
        private double duration;

        public Timer(HistogramMetrics metrics) {
            this.metrics = metrics;
            startNanos = System.nanoTime();
        }

        public void finish() {
            long endNanos = System.nanoTime();
            duration = (double) (endNanos - startNanos) / 1.0E9D;
            metrics.observe(duration);
        }

        @Override
        public void close() {
            finish();
        }
    }
}
