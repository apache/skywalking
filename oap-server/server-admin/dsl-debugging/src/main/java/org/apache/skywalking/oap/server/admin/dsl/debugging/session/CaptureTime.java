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

package org.apache.skywalking.oap.server.admin.dsl.debugging.session;

import java.time.Instant;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * When a node captured a record: the wall clock as epoch milliseconds and the nanoseconds within that millisecond,
 * compared milliseconds first, then nanoseconds. Clients read it as {@code <millis>.<nanos>}, the nanoseconds in six
 * digits, such as {@code 1795505219786.871986}; on a JDK whose clock ticks in microseconds its last three digits are 0.
 */
@Getter
@EqualsAndHashCode
public final class CaptureTime implements Comparable<CaptureTime> {
    /**
     * Before every capture: a read from it starts at the first record.
     */
    public static final CaptureTime EARLIEST = new CaptureTime(Long.MIN_VALUE, 0);
    /**
     * After every capture: a node that captures no more holds every record before it.
     */
    public static final CaptureTime LATEST = new CaptureTime(Long.MAX_VALUE, 999_999);

    private static final int NANOS_PER_MILLI = 1_000_000;

    private final long millis;
    private final int nanos;

    /**
     * @param nanos the nanoseconds within the millisecond, 0 to 999,999
     */
    public CaptureTime(final long millis, final int nanos) {
        if (nanos < 0 || nanos >= NANOS_PER_MILLI) {
            throw new IllegalArgumentException("nanoseconds within a millisecond must be 0 to 999999: " + nanos);
        }
        this.millis = millis;
        this.nanos = nanos;
    }

    public static CaptureTime now() {
        final Instant now = Instant.now();
        return new CaptureTime(now.toEpochMilli(), now.getNano() % NANOS_PER_MILLI);
    }

    /**
     * @param text {@code <millis>.<nanos>}, as {@link #toString()} writes it
     * @throws IllegalArgumentException when the text is not a capture time
     */
    public static CaptureTime parse(final String text) {
        final int dot = text.indexOf('.');
        if (dot <= 0 || text.length() - dot - 1 != 6 || !asciiDigits(text, 0, dot)
            || !asciiDigits(text, dot + 1, text.length())) {
            throw new IllegalArgumentException("not a capture time, <millis>.<six-digit nanos>: " + text);
        }
        try {
            return new CaptureTime(Long.parseLong(text.substring(0, dot)), Integer.parseInt(text.substring(dot + 1)));
        } catch (final NumberFormatException e) {
            throw new IllegalArgumentException("not a capture time, <millis>.<six-digit nanos>: " + text, e);
        }
    }

    private static boolean asciiDigits(final String text, final int from, final int to) {
        for (int i = from; i < to; i++) {
            final char c = text.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }

    @Override
    public int compareTo(final CaptureTime other) {
        final int byMillis = Long.compare(millis, other.millis);
        return byMillis != 0 ? byMillis : Integer.compare(nanos, other.nanos);
    }

    @Override
    public String toString() {
        final String digits = Integer.toString(nanos);
        return millis + "." + "000000".substring(digits.length()) + digits;
    }
}
