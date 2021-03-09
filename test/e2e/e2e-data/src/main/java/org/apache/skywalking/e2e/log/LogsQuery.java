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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LogsQuery extends AbstractQuery<LogsQuery> {

    private String serviceId;
    private String endpointId = "";
    private String endpointName = "";
    private String pageNum = "1";
    private String pageSize = "15";
    private String needTotal = "true";
    private String keywordsOfContent = "";
    private String excludingKeywordsOfContent = "";
    private List<Map<String, String>> tags = Collections.emptyList();

    public String serviceId() {
        return serviceId;
    }

    public LogsQuery serviceId(String serviceId) {
        this.serviceId = serviceId;
        return this;
    }

    public String endpointId() {
        return endpointId;
    }

    public LogsQuery endpointId(String endpointId) {
        this.endpointId = endpointId;
        return this;
    }

    public String endpointName() {
        return endpointName;
    }

    public LogsQuery endpointName(String endpointName) {
        this.endpointName = endpointName;
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

    public LogsQuery keywordsOfContent(String... keywords) {
        this.keywordsOfContent = joinQuotes(keywords);
        return this;
    }

    public String keywordsOfContent() {
        return keywordsOfContent;
    }

    public LogsQuery excludingKeywordsOfContent(String... keywords) {
        this.excludingKeywordsOfContent = joinQuotes(keywords);
        return this;
    }

    public String excludingKeywordsOfContent() {
        return excludingKeywordsOfContent;
    }

    private String joinQuotes(String... keywords) {
        for (int i = 0; i < keywords.length; i++) {
            String keyword = keywords[i];
            keywords[i] = "\"" + keyword + "\"";
        }
        return String.join(",", keywords);
    }

    public List<Map<String, String>> tags() {
        return tags;
    }

    public LogsQuery tags(List<Map<String, String>> tags) {
        this.tags = tags;
        return this;
    }

    public LogsQuery addTag(String key, String value) {
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
