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

import java.util.Arrays;
import java.util.List;
import lombok.Getter;
import lombok.ToString;
import org.apache.skywalking.oap.server.core.analysis.metrics.DataTable;
import org.apache.skywalking.oap.server.core.query.type.Bucket;
import org.apache.skywalking.oap.server.core.query.type.HeatMap;

/**
 * BucketedValues represents a value set, which elements are grouped by time bucket.
 */
@ToString
@Getter
public class BucketedValues {
    /**
     * The element in the buckets represent the minimal value of this bucket, the max is defined by the next element.
     * Such as 0, 10, 50, 100 means buckets are [0, 10), [10, 50), [50, 100), [100, infinite+).
     *
     * The {@link Integer#MIN_VALUE} could be the first bucket element to indicate there is no minimal value.
     */
    private int[] buckets;
    /**
     * {@link #buckets} and {@link #values} arrays should have the same length. The element in the values, represents
     * the amount in the same index bucket.
     */
    private long[] values;

    /**
     * @param buckets Read {@link #buckets}
     * @param values  Read {@link #values}
     */
    public BucketedValues(final int[] buckets, final long[] values) {
        if (buckets == null || values == null || buckets.length == 0 || values.length == 0) {
            throw new IllegalArgumentException("buckets and values can't be null.");
        }
        if (buckets.length != values.length) {
            throw new IllegalArgumentException("The length of buckets and values should be same.");
        }
        this.buckets = buckets;
        this.values = values;
    }

    /**
     * @return true if the bucket is same.
     */
    public boolean isCompatible(DataTable dataset) {
        final List<String> sortedKeys = dataset.sortedKeys(new HeatMap.KeyComparator(true));
        int[] existedBuckets = new int[sortedKeys.size()];
        for (int i = 0; i < sortedKeys.size(); i++) {
            final String key = sortedKeys.get(i);
            if (key.equals(Bucket.INFINITE_NEGATIVE)) {
                existedBuckets[i] = Integer.MIN_VALUE;
            } else {
                existedBuckets[i] = Integer.parseInt(key);
            }
        }

        return Arrays.equals(buckets, existedBuckets);
    }
}
