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

package org.apache.skywalking.oap.server.core.analysis.manual.networkalias;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class NetworkAddressAliasTest {
    @Test
    public void anUnchangedMappingWithinTheIntervalIsNotWrittenAndNotAltered() {
        final NetworkAddressAlias stored = alias("cart", "cart-1", 202610281200L, 202610281200L);
        assertFalse(stored.combine(alias("cart", "cart-1", 202610281204L, 202610281204L)));
        assertEquals(202610281200L, stored.getLastUpdateTimeBucket());
        assertEquals(202610281200L, stored.getTimeBucket());
    }

    @Test
    public void anUnchangedMappingIsRefreshedOnceTheIntervalHasPassed() {
        final NetworkAddressAlias stored = alias("cart", "cart-1", 202610281200L, 202610281200L);
        assertTrue(stored.combine(alias("cart", "cart-1", 202610281205L, 202610281205L)));
        assertEquals(202610281205L, stored.getLastUpdateTimeBucket());
    }

    @Test
    public void anotherServiceOrInstanceIsWrittenAtOnce() {
        final NetworkAddressAlias stored = alias("cart", "cart-1", 202610281200L, 202610281200L);
        assertTrue(stored.combine(alias("payment", "payment-1", 202610281201L, 202610281201L)));
        assertEquals("payment", stored.getRepresentServiceId());
        assertEquals(202610281201L, stored.getLastUpdateTimeBucket());

        assertTrue(stored.combine(alias("payment", "payment-2", 202610281202L, 202610281202L)));
        assertEquals("payment-2", stored.getRepresentServiceInstanceId());
    }

    @Test
    public void theIntervalIsMeasuredInTimeAcrossAnHourAndADay() {
        // 12:58 to 13:02 is 4 minutes, though the buckets differ by 44.
        final NetworkAddressAlias hour = alias("cart", "cart-1", 202610281258L, 202610281258L);
        assertFalse(hour.combine(alias("cart", "cart-1", 202610281302L, 202610281302L)));
        // 23:58 to 00:03 the next day is 5 minutes.
        final NetworkAddressAlias day = alias("cart", "cart-1", 202610282358L, 202610282358L);
        assertTrue(day.combine(alias("cart", "cart-1", 202610290003L, 202610290003L)));
    }

    @Test
    public void aChangedMappingIsWrittenWhateverTheClocksSay() {
        // The address's previous owner wrote its row from a clock an hour fast; the new owner's row looks older.
        final NetworkAddressAlias stored = alias("svc-a", "svc-a-1", 202610281200L, 202610281259L);
        assertTrue(stored.combine(alias("svc-b", "svc-b-1", 202610281201L, 202610281201L)));
        assertEquals("svc-b", stored.getRepresentServiceId());
        assertEquals(202610281201L, stored.getLastUpdateTimeBucket());
    }

    @Test
    public void anUnchangedMappingStoredFromAClockAheadWaitsForItsInterval() {
        final NetworkAddressAlias stored = alias("orders", "orders-1", 202610281200L, 202610281213L);
        assertFalse(stored.combine(alias("orders", "orders-1", 202610281210L, 202610281210L)));
        assertEquals(202610281213L, stored.getLastUpdateTimeBucket());
        assertTrue(stored.combine(alias("orders", "orders-1", 202610281218L, 202610281218L)));
    }

    @Test
    public void aRowWithoutAMinuteUpdateBucketIsWritten() {
        final NetworkAddressAlias stored = alias("cart", "cart-1", 202610281200L, 0L);
        assertTrue(stored.combine(alias("cart", "cart-1", 202610281201L, 202610281201L)));
        assertEquals(202610281201L, stored.getLastUpdateTimeBucket());
    }

    @Test
    public void theTimeBucketKeepsTheFirstInsert() {
        final NetworkAddressAlias stored = alias("cart", "cart-1", 202610281200L, 202610281200L);
        assertTrue(stored.combine(alias("payment", "payment-1", 202610281205L, 202610281205L)));
        assertEquals(202610281200L, stored.getTimeBucket());
    }

    private static NetworkAddressAlias alias(final String serviceId, final String instanceId,
                                             final long timeBucket, final long lastUpdateTimeBucket) {
        final NetworkAddressAlias alias = new NetworkAddressAlias();
        alias.setAddress("frontend->cart:7070");
        alias.setRepresentServiceId(serviceId);
        alias.setRepresentServiceInstanceId(instanceId);
        alias.setTimeBucket(timeBucket);
        alias.setLastUpdateTimeBucket(lastUpdateTimeBucket);
        return alias;
    }
}
