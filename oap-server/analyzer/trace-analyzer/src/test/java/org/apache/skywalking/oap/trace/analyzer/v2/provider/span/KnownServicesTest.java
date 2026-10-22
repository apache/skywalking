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

package org.apache.skywalking.oap.trace.analyzer.v2.provider.span;

import com.google.common.base.Ticker;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnownServicesTest {
    private final KnownServices known = new KnownServices();

    @Test
    void anUngroupedServiceIsNamedByItsWholeName() {
        known.record("cart");
        assertEquals("cart", known.nameOf("cart"));
        assertNull(known.nameOf("ca"));
    }

    @Test
    void aGroupedServiceIsNamedByItsShortName() {
        known.record("shop::cart");
        assertEquals("shop::cart", known.nameOf("cart"));
        assertEquals("shop::cart", known.nameOf("shop::cart"));
        assertFalse(known.contains("cart"), "the short name is a way to find the service, not a service");
    }

    @Test
    void aShortNameTwoGroupsShareNamesNeither() {
        known.record("shop::cart");
        known.record("outlet::cart");
        assertNull(known.nameOf("cart"), "the host cannot say which group's cart was dialed");
        assertEquals("shop::cart", known.nameOf("shop::cart"));
    }

    @Test
    void anUngroupedServiceWinsOverAGroupedOneOfTheSameShortName() {
        known.record("shop::cart");
        known.record("cart");
        assertEquals("cart", known.nameOf("cart"));
    }

    @Test
    void aNameWithNothingAfterTheSeparatorHasNoShortName() {
        known.record("shop::");
        assertNull(known.nameOf(""));
        assertEquals("shop::", known.nameOf("shop::"));
    }

    @Test
    void theGroupAndShortNameSplitAgreesWithRecording() {
        assertEquals("shop", KnownServices.groupOf("shop::cart"));
        assertEquals("cart", KnownServices.shortNameOf("shop::cart"));
        assertNull(KnownServices.groupOf("shop::"), "nothing after the separator is no grouped name");
        assertNull(KnownServices.groupOf("::cart"));
        assertNull(KnownServices.groupOf("cart"));

        assertFalse(known.hasGroupedServices());
        known.record("cart");
        known.record("shop::");
        assertFalse(known.hasGroupedServices(), "neither is a grouped service");
        known.record("shop::cart");
        assertTrue(known.hasGroupedServices());
    }

    @Test
    void aStoredNameIsKnownForADayAfterTheReadWhateverTheLookups() {
        final AtomicLong nanos = new AtomicLong();
        final KnownServices ticking = new KnownServices(new Ticker() {
            @Override
            public long read() {
                return nanos.get();
            }
        });
        // Read from storage at start: a retired ungrouped cart, and shop's flagd that has not emitted here yet.
        ticking.recordStored("cart");
        ticking.recordStored("shop::flagd");
        assertTrue(ticking.contains("cart"));
        assertEquals("shop::flagd", ticking.nameOf("flagd"));

        for (int hour = 0; hour < 23; hour++) {
            nanos.addAndGet(TimeUnit.HOURS.toNanos(1));
            assertTrue(ticking.contains("cart"), "callers keep dialing it, hour " + hour);
        }
        ticking.record("shop::flagd");
        nanos.addAndGet(TimeUnit.HOURS.toNanos(2));
        assertFalse(ticking.contains("cart"), "the lookups did not keep the stored name alive");
        assertTrue(ticking.contains("shop::flagd"), "a span recorded it for real");
        assertEquals("shop::flagd", ticking.nameOf("flagd"));
    }

    @Test
    void aShortNameIsHeldWhileAnyGroupEmitsUnderIt() {
        final AtomicLong nanos = new AtomicLong();
        final KnownServices ticking = new KnownServices(new Ticker() {
            @Override
            public long read() {
                return nanos.get();
            }
        });
        ticking.record("infra::frontend-proxy");
        ticking.recordStored("edge::frontend-proxy");
        assertTrue(ticking.hasShortName("frontend-proxy"));
        assertNull(ticking.nameOf("frontend-proxy"), "shared, so it names neither");
        assertFalse(ticking.hasShortName("frontend"));
        assertFalse(ticking.hasShortName(""));

        nanos.addAndGet(TimeUnit.HOURS.toNanos(25));
        assertFalse(ticking.hasShortName("frontend-proxy"), "neither emitted within the day");
    }

    @Test
    void aShortNameStaysWhileItsServiceEmitsWithoutLookups() {
        final AtomicLong nanos = new AtomicLong();
        final KnownServices ticking = new KnownServices(new Ticker() {
            @Override
            public long read() {
                return nanos.get();
            }
        });
        ticking.record("shop::cart");
        for (int hour = 0; hour < 48; hour++) {
            nanos.addAndGet(TimeUnit.HOURS.toNanos(1));
            ticking.record("shop::cart");
        }
        assertEquals("shop::cart", ticking.nameOf("cart"), "spans alone must keep the short name alive");

        nanos.addAndGet(TimeUnit.HOURS.toNanos(25));
        assertNull(ticking.nameOf("cart"), "a day with neither spans nor lookups forgets it");
        ticking.record("shop::cart");
        assertEquals("shop::cart", ticking.nameOf("cart"));
    }

    @Test
    void aSharedShortNameStaysAmbiguousAcrossLaterSpans() {
        known.record("shop::cart");
        known.record("outlet::cart");
        known.record("shop::cart");
        known.record("outlet::cart");
        assertNull(known.nameOf("cart"));
    }

    @Test
    void aSharedShortNameNamesTheRemainingGroupOnceTheOtherFallsSilent() {
        final AtomicLong nanos = new AtomicLong();
        final KnownServices ticking = new KnownServices(new Ticker() {
            @Override
            public long read() {
                return nanos.get();
            }
        });
        ticking.record("shop::cart");
        ticking.record("outlet::cart");
        assertNull(ticking.nameOf("cart"));

        // outlet's cart is retired; shop's keeps emitting, and callers keep looking the short name up.
        for (int hour = 0; hour < 23; hour++) {
            nanos.addAndGet(TimeUnit.HOURS.toNanos(1));
            ticking.record("shop::cart");
            assertNull(ticking.nameOf("cart"), "outlet's cart emitted within the day, hour " + hour);
        }
        nanos.addAndGet(TimeUnit.HOURS.toNanos(1));
        ticking.record("shop::cart");
        assertEquals("shop::cart", ticking.nameOf("cart"), "a day after outlet's last span only shop's is left");

        ticking.record("outlet::cart");
        assertNull(ticking.nameOf("cart"), "a returning group makes it shared again");
    }

    @Test
    void groupsRacingOnOneShortNameLeaveItAmbiguous() throws InterruptedException {
        final ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < 500; round++) {
                final KnownServices racing = new KnownServices();
                final CountDownLatch start = new CountDownLatch(1);
                final CountDownLatch done = new CountDownLatch(2);
                for (final String name : new String[] {"shop::cart", "outlet::cart"}) {
                    pool.execute(() -> {
                        try {
                            start.await();
                            racing.record(name);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        } finally {
                            done.countDown();
                        }
                    });
                }
                start.countDown();
                assertTrue(done.await(10, TimeUnit.SECONDS));
                assertNull(racing.nameOf("cart"), "round " + round);
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
