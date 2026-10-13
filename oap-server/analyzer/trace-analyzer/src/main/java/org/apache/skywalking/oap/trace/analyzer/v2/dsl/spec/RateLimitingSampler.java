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
 */

package org.apache.skywalking.oap.trace.analyzer.v2.dsl.spec;

/**
 * A per-minute counter: the first {@code rpm} calls in a minute are kept, the rest dropped, and the counter
 * resets when the minute rolls over.
 */
public class RateLimitingSampler {
    private static final long ONE_MINUTE_MILLIS = 60_000L;

    private final int rpm;
    private int counter;
    private long windowStart = System.currentTimeMillis();

    public RateLimitingSampler(final int rpm) {
        this.rpm = rpm;
    }

    /**
     * @return whether this call falls inside the current minute's budget
     */
    public synchronized boolean sample() {
        final long now = System.currentTimeMillis();
        if (now - windowStart >= ONE_MINUTE_MILLIS) {
            windowStart = now;
            counter = 0;
        }
        return ++counter <= rpm;
    }
}
