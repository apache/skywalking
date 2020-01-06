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

package org.apache.skywalking.e2e;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * @author kezhenxu94
 */
public abstract class AbstractQuery<T extends AbstractQuery<?>> {
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HHmmss");
    private static final DateTimeFormatter MINUTE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HHmm");
    private static final DateTimeFormatter DAY_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter MONTH_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");

    private String start;
    private String end;
    private String step = "SECOND";

    public String start() {
        if (start != null) {
            return start;
        }
        if ("SECOND".equals(step())) {
            return LocalDateTime.now(ZoneOffset.UTC).minusMinutes(15).format(TIME_FORMATTER);
        }
        if ("MINUTE".equals(step())) {
            return LocalDateTime.now(ZoneOffset.UTC).minusMinutes(15).format(MINUTE_TIME_FORMATTER);
        }
        if ("DAY".equals(step())) {
            return LocalDateTime.now(ZoneOffset.UTC).minusMinutes(15).format(DAY_TIME_FORMATTER);
        }
        if ("MONTH".equals(step())) {
            return LocalDateTime.now(ZoneOffset.UTC).minusMinutes(15).format(MONTH_TIME_FORMATTER);
        }
        return null;
    }

    public T start(String start) {
        this.start = start;
        return (T) this;
    }

    public T start(LocalDateTime start) {
        if ("MINUTE".equals(step())) {
            this.start = start.format(MINUTE_TIME_FORMATTER);
        } else if ("SECOND".equals(step())) {
            this.start = start.format(TIME_FORMATTER);
        } else if ("DAY".equals(step())) {
            this.start = start.format(DAY_TIME_FORMATTER);
        } else if ("MONTH".equals(step())) {
            this.start = start.format(MONTH_TIME_FORMATTER);
        }
        return (T) this;
    }

    public String end() {
        if (end != null) {
            return end;
        }
        if ("SECOND".equals(step())) {
            return LocalDateTime.now(ZoneOffset.UTC).format(TIME_FORMATTER);
        }
        if ("MINUTE".equals(step())) {
            return LocalDateTime.now(ZoneOffset.UTC).format(MINUTE_TIME_FORMATTER);
        }
        if ("DAY".equals(step())) {
            return LocalDateTime.now(ZoneOffset.UTC).format(DAY_TIME_FORMATTER);
        }
        if ("MONTH".equals(step())) {
            return LocalDateTime.now(ZoneOffset.UTC).format(MONTH_TIME_FORMATTER);
        }
        return null;
    }

    public AbstractQuery end(String end) {
        this.end = end;
        return this;
    }

    public T end(LocalDateTime end) {
        if ("MINUTE".equals(step())) {
            this.end = end.format(MINUTE_TIME_FORMATTER);
        } else if ("SECOND".equals(step())) {
            this.end = end.format(TIME_FORMATTER);
        } else if ("DAY".equals(step())) {
            this.end = end.format(DAY_TIME_FORMATTER);
        } else if ("MONTH".equals(step())) {
            this.end = end.format(MONTH_TIME_FORMATTER);
        }
        return (T) this;
    }

    public String step() {
        return step;
    }

    public T step(String step) {
        this.step = step;
        return (T) this;
    }

    public T stepByMonth() {
        this.step = "MONTH";
        return (T) this;
    }

    public T stepByDay() {
        this.step = "DAY";
        return (T) this;
    }

    public T stepByMinute() {
        this.step = "MINUTE";
        return (T) this;
    }

    public T stepBySecond() {
        this.step = "SECOND";
        return (T) this;
    }
}
