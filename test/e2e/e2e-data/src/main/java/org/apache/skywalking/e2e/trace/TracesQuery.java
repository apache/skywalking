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

package org.apache.skywalking.e2e.trace;

import org.apache.skywalking.e2e.AbstractQuery;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TracesQuery extends AbstractQuery<TracesQuery> {
    private String traceState = "ALL";
    private String pageNum = "1";
    private String pageSize = "15";
    private String needTotal = "true";
    private String queryOrder = "BY_DURATION";
    private List<Map<String, String>> tags = Collections.emptyList();

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

    public TracesQuery pageSize(int pageSize) {
        this.pageSize = String.valueOf(pageSize);
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

    public TracesQuery orderByStartTime() {
        this.queryOrder = "BY_START_TIME";
        return this;
    }

    public List<Map<String, String>> tags() {
        return tags;
    }

    public TracesQuery tags(List<Map<String, String>> tags) {
        this.tags = tags;
        return this;
    }

    public TracesQuery addTag(String key, String value) {
        if (Collections.EMPTY_LIST.equals(tags)) {
            tags = new ArrayList<>();
        }
        Map<String, String> tag = new HashMap<>();
        tag.put("key", key);
        tag.put("value", value);
        tags.add(tag);
        return this;
    }
}
