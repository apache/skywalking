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

package org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.UnexpectedException;
import org.apache.skywalking.oap.server.core.analysis.DownSampling;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.query.enumeration.Step;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.joda.time.DateTime;
import org.joda.time.Days;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

/**
 * Builds the names of Elasticsearch indices. A time series table is stored in dated indices,
 * {@code <physical table>-<yyyyMMdd>}, each holding a group of days. The group size, the day step, comes from the
 * table's {@link IndexRolling}, so a caller only says which table and which time.
 */
public final class IndexNames {
    private static final DateTimeFormatter TIME_BUCKET_FORMATTER = DateTimeFormat.forPattern("yyyyMMdd");
    /**
     * Day groups are counted from this day, so a day lands in the same index whenever the OAP starts.
     */
    private static final DateTime DAY_ONE = TIME_BUCKET_FORMATTER.parseDateTime("20000101");
    private static int DAY_STEP = 1;
    private static int SUPER_DATASET_DAY_STEP = 1;

    private IndexNames() {
    }

    /**
     * @param dayStep             days per index of metrics and records; a value below 2 means 1
     * @param superDatasetDayStep days per index of super dataset records; a value below 1 means the day step
     */
    public static void configure(final int dayStep, final int superDatasetDayStep) {
        DAY_STEP = dayStep > 1 ? dayStep : 1;
        SUPER_DATASET_DAY_STEP = superDatasetDayStep > 0 ? superDatasetDayStep : DAY_STEP;
    }

    /**
     * @return the index a document of the model with the given time bucket is written to
     */
    public static String writeIndex(final Model model, final long timeBucket) {
        final IndexFamily family = IndexFamily.of(model);
        if (family.getRolling() == IndexRolling.NONE) {
            return family.getPhysicalName();
        }
        return datedIndex(family, toDay(timeBucket, model.getDownsampling()));
    }

    /**
     * @return the index the point of a time series is read from
     */
    public static String pointIndex(final IndexFamily family, final long pointOfTB, final Step step) {
        if (StringUtil.isBlank(family.getPhysicalName()) || pointOfTB <= 0) {
            throw new IllegalArgumentException(
                "Arguments [tableName]: " + family.getPhysicalName() + " can not be blank and [pointOfTB]: "
                    + pointOfTB + " can not <= 0");
        }
        return datedIndex(family, toDay(pointOfTB, step));
    }

    /**
     * @return the indices holding the duration's days, in day order, or the table's alias when there is no
     * duration
     */
    public static String[] rangeIndices(final IndexFamily family, @Nullable final Duration duration) {
        if (duration == null) {
            return new String[] {family.getPhysicalName()};
        }
        return rangeIndices(family, duration.getStartTimeBucketInSec(), duration.getEndTimeBucketInSec());
    }

    /**
     * @return the indices holding the days between the two timestamps. The caller decides what a timestamp
     * that is not positive means.
     */
    public static String[] rangeIndicesBetweenTimestamps(final IndexFamily family,
                                                         final long startMillis,
                                                         final long endMillis) {
        return rangeIndices(
            family, TimeBucket.getRecordTimeBucket(startMillis), TimeBucket.getRecordTimeBucket(endMillis));
    }

    /**
     * @return today's index of the time series table
     */
    public static String latestIndex(final IndexFamily family, final long currentTimeMillis) {
        return datedIndex(family, TimeBucket.getTimeBucket(currentTimeMillis, DownSampling.Day));
    }

    /**
     * @param concreteIndexName a dated index name, with or without the namespace
     * @return the first day the index holds
     */
    public static long indexStartDay(final String concreteIndexName) {
        return Long.parseLong(concreteIndexName.substring(concreteIndexName.lastIndexOf(Const.LINE) + 1));
    }

    /**
     * A zero bucket, and an end day before the start day, mean the whole table, as the alias.
     */
    private static String[] rangeIndices(final IndexFamily family, final long startSecondTB, final long endSecondTB) {
        if (startSecondTB == 0 || endSecondTB == 0) {
            return new String[] {family.getPhysicalName()};
        }
        final DateTime startDay = TIME_BUCKET_FORMATTER.parseDateTime(startSecondTB / 1000000 + "");
        final DateTime endDay = TIME_BUCKET_FORMATTER.parseDateTime(endSecondTB / 1000000 + "");
        final List<DateTime> days = new ArrayList<>(16);
        for (int i = 0; i <= Days.daysBetween(startDay, endDay).getDays(); i++) {
            days.add(startDay.plusDays(i));
        }
        if (days.isEmpty()) {
            return new String[] {family.getPhysicalName()};
        }
        final int dayStep = dayStepOf(family);
        return days.stream()
                   .map(day -> family.getPhysicalName() + Const.LINE + rollDay(day, dayStep))
                   .distinct()
                   .toArray(String[]::new);
    }

    private static String datedIndex(final IndexFamily family, final long day) {
        return family.getPhysicalName() + Const.LINE + rollDay(day, dayStepOf(family));
    }

    private static int dayStepOf(final IndexFamily family) {
        switch (family.getRolling()) {
            case DAY_STEP:
                return DAY_STEP;
            case SUPER_DATASET_DAY_STEP:
                return SUPER_DATASET_DAY_STEP;
            default:
                throw new IllegalArgumentException("Index " + family.getPhysicalName() + " has no dated indices");
        }
    }

    /**
     * @return the first day of the group the day falls in. With a day step of 11, 20000105 is in the group of
     * 20000101, 20000115 in the group of 20000112, and 20000123 starts its own group.
     */
    private static long rollDay(final long day, final int dayStep) {
        if (dayStep <= 1) {
            return day;
        }
        return rollDay(TIME_BUCKET_FORMATTER.parseDateTime(String.valueOf(day)), dayStep);
    }

    private static long rollDay(final DateTime day, final int dayStep) {
        if (dayStep <= 1) {
            return Long.parseLong(day.toString(TIME_BUCKET_FORMATTER));
        }
        final int groupOffset = Days.daysBetween(DAY_ONE, day).getDays() % dayStep;
        return Long.parseLong(day.minusDays(groupOffset).toString(TIME_BUCKET_FORMATTER));
    }

    private static long toDay(final long timeBucket, final DownSampling downSampling) {
        switch (downSampling) {
            case Second:
                return timeBucket / 1000000;
            case Minute:
                return timeBucket / 10000;
            case Hour:
                return timeBucket / 100;
            case Day:
                return timeBucket;
            default:
                throw new UnexpectedException("Unexpected down sampling value, " + downSampling);
        }
    }

    private static long toDay(final long pointOfTB, final Step step) {
        switch (step) {
            case SECOND:
                return pointOfTB / 1000000;
            case MINUTE:
                return pointOfTB / 10000;
            case HOUR:
                return pointOfTB / 100;
            case DAY:
                return pointOfTB;
            default:
                throw new UnexpectedException("Unexpected step value, " + step);
        }
    }
}
