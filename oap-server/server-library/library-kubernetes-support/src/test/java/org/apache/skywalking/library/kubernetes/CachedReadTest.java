/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.skywalking.library.kubernetes;

import com.google.common.base.Ticker;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class CachedReadTest {
    private static final Duration REFRESH = Duration.ofMinutes(3);
    /**
     * What the API server answers when the read fails.
     */
    private static final List<String> FAILS = List.of("fails");

    private final AtomicLong nanos = new AtomicLong(TimeUnit.HOURS.toNanos(1));
    private final AtomicInteger reads = new AtomicInteger();
    private final AtomicReference<List<String>> apiServer = new AtomicReference<>(List.of("a"));
    private final CachedRead<List<String>> cached = new CachedRead<>(() -> {
        reads.incrementAndGet();
        if (apiServer.get() == FAILS) {
            throw new KubernetesReadFailedException("list endpoints", "code=503");
        }
        return apiServer.get();
    }, Collections.emptyList(), new Ticker() {
        @Override
        public long read() {
            return nanos.get();
        }
    });

    @Test
    public void readsAgainOnceOlderThanTheCallerAccepts() {
        assertEquals(List.of("a"), cached.get(REFRESH));
        apiServer.set(List.of("b"));
        advance(Duration.ofSeconds(179));
        assertEquals(List.of("a"), cached.get(REFRESH));
        assertEquals(1, reads.get());

        // A caller missing a new pod accepts a younger list.
        assertEquals(List.of("b"), cached.get(Duration.ofMinutes(1)));
        assertEquals(2, reads.get());
        assertEquals(List.of("b"), cached.get(Duration.ofMinutes(1)));
        assertEquals(2, reads.get());
    }

    @Test
    public void aResourceTheApiServerDoesNotServeIsEmptyAndKept() {
        apiServer.set(null);
        assertEquals(Collections.emptyList(), cached.get(REFRESH));
        assertEquals(Collections.emptyList(), cached.get(REFRESH));
        assertEquals(1, reads.get());
    }

    @Test
    public void aResourceNotServedIsNotReadAgainBeforeTheListPeriodEvenForAYoungerAge() {
        apiServer.set(null);
        cached.get(REFRESH);
        advance(Duration.ofMinutes(2));
        assertEquals(Collections.emptyList(), cached.get(Duration.ofMinutes(1)));
        assertEquals(1, reads.get());

        advance(Duration.ofMinutes(1));
        apiServer.set(List.of("a"));
        assertEquals(List.of("a"), cached.get(Duration.ofMinutes(1)));
        assertEquals(2, reads.get());
    }

    @Test
    public void aFailedReadIsNotReadAgainBeforeTheListPeriodEvenForAYoungerAge() {
        cached.get(REFRESH);
        advance(REFRESH);
        apiServer.set(FAILS);
        assertEquals(List.of("a"), cached.get(REFRESH));
        advance(Duration.ofMinutes(2));
        assertEquals(List.of("a"), cached.get(Duration.ofMinutes(1)));
        assertEquals(2, reads.get());

        advance(Duration.ofMinutes(1));
        apiServer.set(List.of("b"));
        assertEquals(List.of("b"), cached.get(Duration.ofMinutes(1)));
    }

    @Test
    public void aFailedReadKeepsWhatWasReadBefore() {
        assertEquals(List.of("a"), cached.get(REFRESH));
        advance(REFRESH);
        apiServer.set(FAILS);
        assertEquals(List.of("a"), cached.get(REFRESH));
        assertEquals(List.of("a"), cached.get(REFRESH));
        assertEquals(2, reads.get());

        advance(REFRESH);
        apiServer.set(List.of("c"));
        assertEquals(List.of("c"), cached.get(REFRESH));
    }

    @Test
    public void aFailedFirstReadIsKeptSoTheCallersWithinTheAgeGetItWithoutReading() {
        apiServer.set(FAILS);
        assertThrows(KubernetesReadFailedException.class, () -> cached.get(REFRESH));
        assertThrows(KubernetesReadFailedException.class, () -> cached.get(REFRESH));
        assertEquals(1, reads.get());

        advance(REFRESH);
        apiServer.set(List.of("a"));
        assertEquals(List.of("a"), cached.get(REFRESH));
        assertEquals(2, reads.get());
    }

    private void advance(final Duration duration) {
        nanos.addAndGet(duration.toNanos());
    }
}
