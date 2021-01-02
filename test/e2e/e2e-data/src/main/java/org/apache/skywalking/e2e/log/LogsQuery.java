/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.skywalking.e2e.log;

import org.apache.skywalking.e2e.AbstractQuery;

public class LogsQuery extends AbstractQuery<LogsQuery> {

    private String metricName = "log";
    private String state = "ALL";
    private String pageNum = "1";
    private String pageSize = "15";
    private String needTotal = "true";

    public String metricName() {
        return metricName;
    }

    public LogsQuery metricName(String metricName) {
        this.metricName = metricName;
        return this;
    }

    public String state() {
        return state;
    }

    public LogsQuery state(String state) {
        this.state = state;
        return this;
    }

    public String pageNum() {
        return pageNum;
    }

    public LogsQuery pageNum(String pageNum) {
        this.pageNum = pageNum;
        return this;
    }

    public String pageSize() {
        return pageSize;
    }

    public LogsQuery pageSize(String pageSize) {
        this.pageSize = pageSize;
        return this;
    }

    public String needTotal() {
        return needTotal;
    }

    public LogsQuery needTotal(String needTotal) {
        this.needTotal = needTotal;
        return this;
    }
}
