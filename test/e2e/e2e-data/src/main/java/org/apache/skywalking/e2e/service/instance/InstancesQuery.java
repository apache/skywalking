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

package org.apache.skywalking.e2e.service.instance;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public class InstancesQuery {
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HHmmss");

    private String start = LocalDateTime.now(ZoneOffset.UTC).format(TIME_FORMATTER);
    private String end = LocalDateTime.now(ZoneOffset.UTC).minusMinutes(15).format(TIME_FORMATTER);
    private String step = "SECOND";
    private String serviceId;

    public String serviceId() {
        return serviceId;
    }

    public InstancesQuery serviceId(String serviceId) {
        this.serviceId = serviceId;
        return this;
    }

    public String start() {
        return start;
    }

    public InstancesQuery start(String start) {
        this.start = start;
        return this;
    }

    public InstancesQuery start(LocalDateTime start) {
        this.start = start.format(TIME_FORMATTER);
        return this;
    }

    public String end() {
        return end;
    }

    public InstancesQuery end(String end) {
        this.end = end;
        return this;
    }

    public InstancesQuery end(LocalDateTime end) {
        this.end = end.format(TIME_FORMATTER);
        return this;
    }

    public String step() {
        return step;
    }

    public InstancesQuery step(String step) {
        this.step = step;
        return this;
    }
}
