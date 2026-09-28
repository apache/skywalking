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
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.chrono.IsoChronology;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.time.temporal.ChronoField;
import javax.annotation.Nullable;
import org.apache.skywalking.oap.server.library.util.StringUtil;

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

    /**
     * A time as Session Data defines it: a four-digit year, the letter T, the time of day to the second with a
     * fraction of up to nine digits or none, and Z or an offset such as +08:00. java.time's ISO reader also takes a
     * time without seconds and years of other widths, which the format does not.
     */
    private static final DateTimeFormatter RFC_3339 = new DateTimeFormatterBuilder()
        .appendValue(ChronoField.YEAR, 4)
        .appendLiteral('-')
        .appendValue(ChronoField.MONTH_OF_YEAR, 2)
        .appendLiteral('-')
        .appendValue(ChronoField.DAY_OF_MONTH, 2)
        .appendLiteral('T')
        .appendValue(ChronoField.HOUR_OF_DAY, 2)
        .appendLiteral(':')
        .appendValue(ChronoField.MINUTE_OF_HOUR, 2)
        .appendLiteral(':')
        .appendValue(ChronoField.SECOND_OF_MINUTE, 2)
        .optionalStart()
        .appendFraction(ChronoField.NANO_OF_SECOND, 1, 9, true)
        .optionalEnd()
        .appendOffset("+HH:MM", "Z")
        .toFormatter()
        .withResolverStyle(ResolverStyle.STRICT)
        .withChronology(IsoChronology.INSTANCE);

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
     * Orders two record times by the instant they name. RFC 3339 text does not sort as text when its fractions differ
     * in length, and the Sessionizer's plugin drops trailing zeros: as text, .11 sorts before .1, and .5 before the
     * whole second. Two spellings of one instant are the same time, and a time that does not parse sorts after every
     * one that does; the caller decides between equal times by where each record was read.
     *
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
     * @param rfc3339 a header's <code>at</code>
     * @return the stamp used in the landed file's name, or null when absent or unparseable
     */
    @Nullable
    public static String fileStamp(@Nullable final String rfc3339) {
        final Instant t = instant(rfc3339);
        return t == null ? null : FILE_STAMP.format(t);
    }

    /** A time as {@link #RFC_3339} reads it; null when it is not one. */
    @Nullable
    private static Instant instant(@Nullable final String rfc3339) {
        if (StringUtil.isEmpty(rfc3339)) {
            return null;
        }
        try {
            return OffsetDateTime.parse(rfc3339, RFC_3339).toInstant();
        } catch (final DateTimeParseException e) {
            return null;
        }
    }
}
