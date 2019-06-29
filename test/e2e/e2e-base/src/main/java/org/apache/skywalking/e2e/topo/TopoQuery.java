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

package org.apache.skywalking.e2e.topo;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * @author kezhenxu94
 */
public class TopoQuery {
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HHmmss");
    private static final DateTimeFormatter MINUTE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HHmm");

    private String start = LocalDateTime.now(ZoneOffset.UTC).format(TIME_FORMATTER);
    private String end = LocalDateTime.now(ZoneOffset.UTC).minusMinutes(15).format(TIME_FORMATTER);
    private String step = "SECOND";

    public String start() {
        return start;
    }

    public TopoQuery start(String start) {
        this.start = start;
        return this;
    }

    public TopoQuery start(LocalDateTime start) {
        if ("MINUTE".equals(step())) {
            this.start = start.format(MINUTE_TIME_FORMATTER);
        } else if ("SECOND".equals(step())) {
            this.start = start.format(TIME_FORMATTER);
        }
        return this;
    }

    public String end() {
        return end;
    }

    public TopoQuery end(String end) {
        this.end = end;
        return this;
    }

    public TopoQuery end(LocalDateTime end) {
        if ("MINUTE".equals(step())) {
            this.end = end.format(MINUTE_TIME_FORMATTER);
        } else if ("SECOND".equals(step())) {
            this.end = end.format(TIME_FORMATTER);
        }
        return this;
    }

    public String step() {
        return step;
    }

    public TopoQuery step(String step) {
        this.step = step;
        return this; }
}
