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

package org.apache.skywalking.oap.server.core.query.type;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeatMapTest {
    @Test
    void aMeterHistogramsOverflowBucketBuildsAColumn() {
        // A meter histogram keeps values above its last bound under Long.MAX_VALUE, the key MAL gives OTel's +Inf
        // bucket and TAL's overflow bucket; an int parse used to fail the whole query.
        final HeatMap heatMap = new HeatMap();
        heatMap.buildColumn("202610120800", "10,1|20,4|3000000000,2|9223372036854775807,3", 0);

        final List<Bucket> buckets = heatMap.getBuckets();
        assertEquals(4, buckets.size());
        assertEquals(10, buckets.get(0).duration());
        assertEquals(Integer.MAX_VALUE, buckets.get(1).duration(), "a bound beyond an int");
        assertEquals(Integer.MAX_VALUE, buckets.get(2).duration(), "up to the overflow key");
        assertTrue(buckets.get(3).isInfiniteMax());
        assertEquals(List.of(1L, 4L, 2L, 3L), heatMap.getValues().get(0).getValues());
    }

    @Test
    void aBucketWiderThanAnIntReadsAsTheWidestInt() {
        assertEquals(Integer.MAX_VALUE, new Bucket().setMin(10L).setMax(Long.MAX_VALUE).duration());
        assertEquals(Integer.MAX_VALUE, new Bucket().setMin(-5L).setMax(Long.MAX_VALUE).duration());
        assertEquals(15, new Bucket().setMin(10L).setMax(25L).duration());
    }
}
