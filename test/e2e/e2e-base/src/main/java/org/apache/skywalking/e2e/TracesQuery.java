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
public class TracesQuery {
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HHmmss");

    private String start = LocalDateTime.now(ZoneOffset.UTC).format(TIME_FORMATTER);
    private String end = LocalDateTime.now(ZoneOffset.UTC).minusMinutes(15).format(TIME_FORMATTER);
    private String step = "SECOND";
    private String traceState = "ALL";
    private String pageNum = "1";
    private String pageSize = "15";
    private String needTotal = "true";
    private String queryOrder = "BY_DURATION";

    public String start() {
        return start;
    }

    public TracesQuery start(String start) {
        this.start = start;
        return this;
    }

    public TracesQuery start(LocalDateTime start) {
        this.start = start.format(TIME_FORMATTER);
        return this;
    }

    public String end() {
        return end;
    }

    public TracesQuery end(String end) {
        this.end = end;
        return this;
    }

    public TracesQuery end(LocalDateTime end) {
        this.end = end.format(TIME_FORMATTER);
        return this;
    }

    public String step() {
        return step;
    }

    public TracesQuery step(String step) {
        this.step = step;
        return this;
    }

    public String traceState() {
        return traceState;
    }

    public TracesQuery traceState(String traceState) {
        this.traceState = traceState;
        return this;
    }

    public String pageNum() {
        return pageNum;
    }

    public TracesQuery pageNum(String pageNum) {
        this.pageNum = pageNum;
        return this;
    }

    public TracesQuery pageNum(int pageNum) {
        this.pageNum = String.valueOf(pageNum);
        return this;
    }

    public String pageSize() {
        return pageSize;
    }

    public TracesQuery pageSize(String pageSize) {
        this.pageSize = pageSize;
        return this;
    }

    public String needTotal() {
        return needTotal;
    }

    public TracesQuery needTotal(boolean needTotal) {
        this.needTotal = String.valueOf(needTotal);
        return this;
    }

    public String queryOrder() {
        return queryOrder;
    }

    public TracesQuery queryOrder(String queryOrder) {
        this.queryOrder = queryOrder;
        return this;
    }

    public TracesQuery orderByDuration() {
        this.queryOrder = "BY_DURATION";
        return this;
    }

    public TracesQuery pageSize(int pageSize) {
        this.pageSize = String.valueOf(pageSize);
        return this;
    }
}
