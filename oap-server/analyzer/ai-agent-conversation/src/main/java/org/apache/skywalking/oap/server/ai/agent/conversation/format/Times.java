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

package org.apache.skywalking.oap.server.ai.agent.conversation.format;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import javax.annotation.Nullable;

/**
 * Session Data and Session Flow carry RFC 3339 strings, because their bytes are digested. A view carries unix
 * milliseconds, because it is read and never digested.
 */
public final class Times {
    /**
     * The stamp in a landed file's name: <code>20260904T033423.840913000Z</code>, from the header's <code>at</code>.
     */
    private static final DateTimeFormatter FILE_STAMP =
        DateTimeFormatter.ofPattern("uuuuMMdd'T'HHmmss.nnnnnnnnn'Z'").withZone(ZoneOffset.UTC);

    private Times() {
    }

    /**
     * @param rfc3339 a time as the runtime wrote it, or null or empty
     * @return unix milliseconds, or 0 when absent or unparseable
     */
    public static long millis(final String rfc3339) {
        final Instant t = instant(rfc3339);
        return t == null ? 0L : t.toEpochMilli();
    }

    /**
     * @param rfc3339 a time as the Sessionizer writes it, with a zone offset or Z
     * @return the time in nanoseconds since the epoch, the precision the Sessionizer computes with, or 0
     */
    public static long nanos(final String rfc3339) {
        final Instant t = instant(rfc3339);
        return t == null ? 0L : t.getEpochSecond() * 1_000_000_000L + t.getNano();
    }

    /**
     * Orders two record times by the instant they name, as the Sessionizer's <code>compareTimes</code> does. RFC 3339
     * text does not sort as text when its fractions differ in length, and Go's <code>RFC3339Nano</code>, which the
     * Sessionizer's plugin writes, drops trailing zeros: as text, .11 sorts before .1, and .5 before the whole second.
     * Two spellings of one instant are the same time, and a time that does not parse sorts after every one
     * that does; the caller decides between equal times by where each record was read, never by the text.
     *
     * @param a a time as written
     * @param b a time as written
     * @return negative, zero or positive as <code>a</code> is before, at or after <code>b</code>
     */
    public static int compare(final String a, final String b) {
        final Instant ta = instant(a);
        final Instant tb = instant(b);
        if (ta != null && tb != null) {
            return ta.compareTo(tb);
        }
        if ((ta == null) != (tb == null)) {
            return ta != null ? -1 : 1;
        }
        return 0;
    }

    /**
     * RFC 3339 as Go's <code>time.Parse(time.RFC3339Nano, s)</code> reads it, so every reader orders and times records
     * the same way. Measured on Go 1.27, it takes a date and a time of exactly <code>2006-01-02T15:04:05</code>, each
     * part in range and the month's days counted; a fraction after <code>.</code> or <code>,</code> of one digit or
     * more, of any length, kept to the nanosecond; and <code>Z</code>, or an offset <code>+hh:mm</code> or
     * <code>-hh:mm</code> whose hours go to 24 and whose minutes go to 60. Nothing else: no lowercase <code>t</code>
     * or <code>z</code>, no <code>+0800</code>, no <code>+08</code>, nothing after the zone.
     */
    @Nullable
    private static Instant instant(@Nullable final String s) {
        if (s == null || s.length() < 19 || s.charAt(4) != '-' || s.charAt(7) != '-' || s.charAt(10) != 'T'
            || s.charAt(13) != ':' || s.charAt(16) != ':') {
            return null;
        }
        final int year = digits(s, 0, 4);
        final int month = digits(s, 5, 2);
        final int day = digits(s, 8, 2);
        final int hour = digits(s, 11, 2);
        final int minute = digits(s, 14, 2);
        final int second = digits(s, 17, 2);
        if (year < 0 || month < 1 || month > 12 || day < 1 || day > YearMonth.of(year, month).lengthOfMonth()
            || hour < 0 || hour > 23 || minute < 0 || minute > 59 || second < 0 || second > 59) {
            return null;
        }
        int i = 19;
        long nanos = 0;
        if (i < s.length() && (s.charAt(i) == '.' || s.charAt(i) == ',')) {
            int n = i + 1;
            while (n < s.length() && s.charAt(n) >= '0' && s.charAt(n) <= '9') {
                n++;
            }
            if (n == i + 1) {
                return null;
            }
            // the first nine digits, as many nanoseconds as they say; the rest are dropped
            for (int k = 0; k < 9; k++) {
                final int at = i + 1 + k;
                nanos = nanos * 10 + (at < n ? s.charAt(at) - '0' : 0);
            }
            i = n;
        }
        long offsetSeconds = 0;
        final String zone = s.substring(i);
        if (!"Z".equals(zone)) {
            if (zone.length() != 6 || (zone.charAt(0) != '+' && zone.charAt(0) != '-') || zone.charAt(3) != ':') {
                return null;
            }
            final int zh = digits(zone, 1, 2);
            final int zm = digits(zone, 4, 2);
            if (zh < 0 || zh > 24 || zm < 0 || zm > 60) {
                return null;
            }
            offsetSeconds = (zh * 60L + zm) * 60L * (zone.charAt(0) == '-' ? -1 : 1);
        }
        final long local = LocalDateTime.of(year, month, day, hour, minute, second).toEpochSecond(ZoneOffset.UTC);
        return Instant.ofEpochSecond(local - offsetSeconds, nanos);
    }

    /** The number the ASCII digits at a place spell, or -1 when one of them is not a digit. */
    private static int digits(final String s, final int from, final int count) {
        int v = 0;
        for (int k = from; k < from + count; k++) {
            final char c = s.charAt(k);
            if (c < '0' || c > '9') {
                return -1;
            }
            v = v * 10 + (c - '0');
        }
        return v;
    }

    /**
     * @param rfc3339 a header's <code>at</code>
     * @return the stamp used in the landed file's name, or null when absent or unparseable
     */
    @Nullable
    public static String fileStamp(@Nullable final String rfc3339) {
        final Instant t = instant(rfc3339);
        return t == null ? null : FILE_STAMP.format(t);
    }
}
