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

import java.time.Duration;
import java.util.function.Supplier;

/**
 * The result of a read the readers of {@link LookupCache} share, such as the list of all endpoints. It is read on the
 * calling thread, a background reader, when it was not read within the age the caller accepts; callers arriving
 * meanwhile wait for that read.
 * <ul>
 *     <li>A read the API server answers with a 404, as it does not serve the resource, gives the empty value.</li>
 *     <li>A read that fails keeps the value read before, if any; with none, the failure is kept, so the callers get it
 *     without reading again.</li>
 *     <li>After a 404 or a failure, the next read is not before {@link #LIST_REFRESH_PERIOD}, even for a caller
 *     accepting a younger result: a resource not served, or an API server failing, is not read again sooner.</li>
 * </ul>
 *
 * @param <V> the data read
 */
public final class CachedRead<V> {
    /**
     * How long a list of the whole cluster, such as all endpoints, is used before it is read again.
     */
    public static final Duration LIST_REFRESH_PERIOD = Duration.ofMinutes(3);

    private final Supplier<V> reader;
    private final V empty;
    private final Ticker ticker;

    private V value;
    private KubernetesReadFailedException failure;
    private long readNanos;
    private boolean lastReadReturnedData;

    /**
     * @param reader reads the data, returns null for a 404 and throws {@link KubernetesReadFailedException} when the
     *               read failed, as {@link KubernetesApiReads#read} does
     * @param empty  what a 404 gives
     */
    public CachedRead(final Supplier<V> reader, final V empty) {
        this(reader, empty, Ticker.systemTicker());
    }

    CachedRead(final Supplier<V> reader, final V empty, final Ticker ticker) {
        this.reader = reader;
        this.empty = empty;
        this.ticker = ticker;
    }

    /**
     * @param maxAge how long ago the data may have been read
     * @throws KubernetesReadFailedException when no read has succeeded yet and the last one, within {@code maxAge},
     *                                       failed
     */
    public synchronized V get(final Duration maxAge) {
        final long maxAgeNanos = lastReadReturnedData
            ? maxAge.toNanos()
            : Math.max(maxAge.toNanos(), LIST_REFRESH_PERIOD.toNanos());
        if ((value == null && failure == null) || ticker.read() - readNanos >= maxAgeNanos) {
            read();
        }
        if (value == null) {
            throw failure;
        }
        return value;
    }

    private void read() {
        try {
            final V read = reader.get();
            value = read == null ? empty : read;
            failure = null;
            lastReadReturnedData = read != null;
        } catch (final KubernetesReadFailedException e) {
            if (value == null) {
                failure = e;
            }
            lastReadReturnedData = false;
        }
        readNanos = ticker.read();
    }
}
