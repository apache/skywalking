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

package org.apache.skywalking.oap.server.core.analysis.meter.function;

import java.util.Comparator;
import org.apache.skywalking.oap.server.core.query.type.Bucket;
import org.apache.skywalking.oap.server.core.query.type.HeatMap;

/**
 * Dataset keys for meter histogram buckets: finite longs as decimal strings, {@link Long#MIN_VALUE}
 * as {@link Bucket#INFINITE_NEGATIVE}, and the overflow (+Inf) bucket as {@link Long#MAX_VALUE}.
 */
public final class HistogramBucketKey {
    private HistogramBucketKey() {
    }

    public static final Comparator<String> ASC = new HeatMap.KeyComparator(true);

    public static long toBound(final String key) {
        if (Bucket.INFINITE_NEGATIVE.equals(key)) {
            return Long.MIN_VALUE;
        }
        return Long.parseLong(key);
    }

    /**
     * Percentile result for the bucket that absorbed the rank. When that bucket is the overflow
     * (+Inf / {@link Long#MAX_VALUE}) and a finite upper bound exists, return the highest finite
     * bound (Prometheus {@code histogram_quantile} style) instead of leaking {@link Long#MAX_VALUE}.
     */
    public static long percentileBound(final String key, final Long highestFiniteBound) {
        final long bound = toBound(key);
        if (bound == Long.MAX_VALUE && highestFiniteBound != null) {
            return highestFiniteBound;
        }
        return bound;
    }

    /**
     * Track the highest finite bucket upper bound seen while walking keys in ascending order.
     */
    public static Long advanceHighestFinite(final String key, final Long current) {
        if (Bucket.INFINITE_NEGATIVE.equals(key)) {
            return current;
        }
        final long bound = Long.parseLong(key);
        if (bound == Long.MAX_VALUE) {
            return current;
        }
        return bound;
    }
}
