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

package org.apache.skywalking.library.banyandb.v1.client.metadata;

import com.google.common.base.Strings;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode
public class Duration {
    private static final Pattern DURATION_PATTERN =
            Pattern.compile("(((?<day>\\d+)d)?((?<hour>\\d+)h)?((?<minute>\\d+)m)?|0)");
    private static final long MINUTES_PER_HOUR = 60;
    private static final long HOURS_PER_DAY = 24;
    private static final long MINUTES_PER_DAY = MINUTES_PER_HOUR * HOURS_PER_DAY;

    @EqualsAndHashCode.Exclude
    private volatile String text;
    private final long minutes;

    private Duration(long minutes) {
        this.minutes = minutes;
    }

    public String format() {
        if (!Strings.isNullOrEmpty(text)) {
            return text;
        }

        final StringBuilder builder = new StringBuilder();
        long minutes = this.minutes;
        if (minutes >= MINUTES_PER_DAY) {
            long weeks = minutes / MINUTES_PER_DAY;
            builder.append(weeks).append("d");
            minutes = minutes % MINUTES_PER_DAY;
        }
        if (minutes >= MINUTES_PER_HOUR) {
            long weeks = minutes / MINUTES_PER_HOUR;
            builder.append(weeks).append("h");
            minutes = minutes % MINUTES_PER_HOUR;
        }
        if (minutes > 0) {
            builder.append(minutes).append("m");
        }
        this.text = builder.toString();
        return this.text;
    }

    public Duration add(Duration duration) {
        return new Duration(this.minutes + duration.minutes);
    }

    public static Duration parse(String text) {
        if (Strings.isNullOrEmpty(text)) {
            return new Duration(0);
        }
        Matcher matcher = DURATION_PATTERN.matcher(text);
        if (!matcher.find()) {
            return new Duration(0);
        }
        long total = 0;
        final String days = matcher.group("day");
        if (!Strings.isNullOrEmpty(days)) {
            total += Long.parseLong(days) * MINUTES_PER_DAY;
        }
        final String hours = matcher.group("hour");
        if (!Strings.isNullOrEmpty(hours)) {
            total += Long.parseLong(hours) * MINUTES_PER_HOUR;
        }
        final String minutes = matcher.group("minute");
        if (!Strings.isNullOrEmpty(minutes)) {
            total += Long.parseLong(minutes);
        }
        return new Duration(total);
    }

    public static Duration ofMinutes(long minutes) {
        return new Duration(minutes);
    }

    public static Duration ofHours(long hours) {
        return new Duration(hours * MINUTES_PER_HOUR);
    }

    public static Duration ofDays(long days) {
        return ofHours(days * HOURS_PER_DAY);
    }
}
