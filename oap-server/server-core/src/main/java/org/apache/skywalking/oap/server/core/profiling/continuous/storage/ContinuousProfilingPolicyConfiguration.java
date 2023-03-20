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

package org.apache.skywalking.oap.server.core.profiling.continuous.storage;

import com.google.gson.Gson;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.skywalking.oap.server.core.query.input.ContinuousProfilingPolicyItemCreation;
import org.apache.skywalking.oap.server.core.query.input.ContinuousProfilingPolicyCreation;
import org.apache.skywalking.oap.server.core.query.input.ContinuousProfilingPolicyTargetCreation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class ContinuousProfilingPolicyConfiguration {
    private static Gson GSON = new Gson();

    // one target have multiple checkers
    private Map<ContinuousProfilingTargetType, Map<ContinuousProfilingMonitorType, CheckItem>> targetCheckers;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CheckItem {
        private String threshold;
        private int period;
        private int count;
        private List<String> uriList;
        private String uriRegex;
    }

    public String toJSON() {
        return GSON.toJson(this);
    }

    public static ContinuousProfilingPolicyConfiguration buildFromRequest(ContinuousProfilingPolicyCreation request) {
        final ContinuousProfilingPolicyConfiguration data = new ContinuousProfilingPolicyConfiguration();
        for (ContinuousProfilingPolicyTargetCreation target : request.getTargets()) {
            final ContinuousProfilingTargetType targetType = target.getTargetType();
            Map<ContinuousProfilingMonitorType, CheckItem> items = data.targetCheckers.computeIfAbsent(targetType, k -> new HashMap<>());

            for (ContinuousProfilingPolicyItemCreation itemRequest : target.getCheckItems()) {
                final CheckItem item = new CheckItem();
                item.setThreshold(itemRequest.getThreshold());
                item.setPeriod(itemRequest.getPeriod());
                item.setCount(itemRequest.getCount());
                item.setUriList(itemRequest.getUriList());
                item.setUriRegex(itemRequest.getUriRegex());
                items.put(itemRequest.getType(), item);
            }
        }
        return data;
    }

    public static ContinuousProfilingPolicyConfiguration parseFromJSON(String json) {
        return GSON.fromJson(json, ContinuousProfilingPolicyConfiguration.class);
    }

    public ContinuousProfilingPolicyConfiguration() {
        this.targetCheckers = new HashMap<>();
    }
}