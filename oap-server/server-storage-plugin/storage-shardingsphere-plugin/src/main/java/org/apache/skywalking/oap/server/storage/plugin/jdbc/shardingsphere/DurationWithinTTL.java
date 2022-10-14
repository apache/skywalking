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

package org.apache.skywalking.oap.server.storage.plugin.jdbc.shardingsphere;

import lombok.Setter;
import org.apache.skywalking.oap.server.core.UnexpectedException;
import org.apache.skywalking.oap.server.core.config.ConfigService;
import org.apache.skywalking.oap.server.core.query.DurationUtils;
import org.apache.skywalking.oap.server.core.query.enumeration.Step;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

public enum DurationWithinTTL {
    INSTANCE;

    private static final DateTimeFormatter YYYYMMDD = DateTimeFormat.forPattern("yyyyMMdd");
    private static final DateTimeFormatter YYYYMMDDHH = DateTimeFormat.forPattern("yyyyMMddHH");
    private static final DateTimeFormatter YYYYMMDDHHMM = DateTimeFormat.forPattern("yyyyMMddHHmm");
    private static final DateTimeFormatter YYYYMMDDHHMMSS = DateTimeFormat.forPattern("yyyyMMddHHmmss");

    @Setter
    private ConfigService configService;

    /**
     * @param duration Origin Duration instance
     * @return new Duration instance and the time range within the Metrics TTL, if origin duration is null then return null.
     */
    public Duration getMetricDurationWithinTTL(final Duration duration) {
        if (duration == null) {
            return null;
        }
        Duration durationWithinTTL = new Duration();
        durationWithinTTL.setStart(DurationWithinTTL.INSTANCE.getTrimmedMetricStartTime(duration.getStep(), duration.getStart()));
        durationWithinTTL.setEnd(DurationWithinTTL.INSTANCE.getTrimmedMetricEndTime(duration.getStep(), duration.getEnd()));
        durationWithinTTL.setStep(duration.getStep());

        return durationWithinTTL;
    }

    /**
     * @param duration Origin Duration instance
     * @return new Duration instance and the time range within the Record TTL, if origin duration is null then return null.
     */
    public Duration getRecordDurationWithinTTL(final Duration duration) {
        if (duration == null) {
            return null;
        }
        Duration durationWithinTTL = new Duration();
        durationWithinTTL.setStart(DurationWithinTTL.INSTANCE.getTrimmedRecordStartTime(duration.getStep(), duration.getStart()));
        durationWithinTTL.setEnd(DurationWithinTTL.INSTANCE.getTrimmedRecordEndTime(duration.getStep(), duration.getEnd()));
        durationWithinTTL.setStep(duration.getStep());

        return durationWithinTTL;
    }

    // Trim the startStr according to the record TTL, for query
    public String getTrimmedRecordStartTime(Step step, String startStr) {
        if (configService == null) {
            throw new UnexpectedException("ConfigService can not be null, should set ConfigService first.");
        }
        return trimStartTime(step, startStr, configService.getRecordDataTTL());
    }

    // Trim the startStr according to the metric TTL, for query
    public String getTrimmedMetricStartTime(Step step, String startStr) {
        if (configService == null) {
            throw new UnexpectedException("ConfigService can not be null, should set ConfigService first.");
        }
        return trimStartTime(step, startStr, configService.getMetricsDataTTL());
    }

    // Trim the endStr according to the record TTL, for query
    public String getTrimmedRecordEndTime(Step step, String endStr) {
        return trimEndTime(step, endStr);
    }

    // Trim the endStr according to the metric TTL, for query
    public String getTrimmedMetricEndTime(Step step, String endStr) {
        return trimEndTime(step, endStr);
    }

    private String trimStartTime(Step step, String startStr, int ttl) {
        long startDate = DurationUtils.INSTANCE.convertToTimeBucket(step, startStr);
        long timeFloor = Long.parseLong(DateTime.now().plusDays(1 - ttl).toString("yyyyMMdd"));
        switch (step) {
            case DAY:
                return YYYYMMDD.parseDateTime(String.valueOf(Math.max(timeFloor, startDate))).toString("yyyy-MM-dd");
            case HOUR:
                timeFloor = timeFloor * 100;
                return YYYYMMDDHH.parseDateTime(String.valueOf(Math.max(timeFloor, startDate))).toString("yyyy-MM-dd HH");
            case MINUTE:
                timeFloor = timeFloor * 100 * 100;
                return YYYYMMDDHHMM.parseDateTime(String.valueOf(Math.max(timeFloor, startDate))).toString("yyyy-MM-dd HHmm");
            case SECOND:
                timeFloor = timeFloor * 100 * 100 * 100;
                return YYYYMMDDHHMMSS.parseDateTime(String.valueOf(Math.max(timeFloor, startDate))).toString("yyyy-MM-dd HHmmss");
        }

        throw new UnexpectedException("Unsupported step " + step.name());
    }

    // Trim the endStr according to the real date, for query
    public String trimEndTime(Step step, String endStr) {
        long endDate = DurationUtils.INSTANCE.convertToTimeBucket(step, endStr);
        long timeCeiling = Long.parseLong(DateTime.now().toString("yyyyMMdd"));
        switch (step) {
            case DAY:
                return YYYYMMDD.parseDateTime(String.valueOf(Math.min(endDate, timeCeiling))).toString("yyyy-MM-dd");
            case HOUR:
                timeCeiling = (timeCeiling * 100) + 23;
                return YYYYMMDDHH.parseDateTime(String.valueOf(Math.min(endDate, timeCeiling))).toString("yyyy-MM-dd HH");
            case MINUTE:
                timeCeiling = ((timeCeiling * 100) + 23) * 100 + 59;
                return YYYYMMDDHHMM.parseDateTime(String.valueOf(Math.min(endDate, timeCeiling))).toString("yyyy-MM-dd HHmm");
            case SECOND:
                timeCeiling = (((timeCeiling * 100) + 23) * 100 + 59) * 100 + 59;
                return YYYYMMDDHHMMSS.parseDateTime(String.valueOf(Math.min(endDate, timeCeiling))).toString("yyyy-MM-dd HHmmss");
        }

        throw new UnexpectedException("Unsupported step " + step.name());
    }
}
