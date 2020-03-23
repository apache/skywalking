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

package org.apache.skywalking.apm.agent.core.context;

import org.apache.skywalking.apm.agent.core.base64.Base64;
import org.apache.skywalking.apm.agent.core.conf.Config;
import org.apache.skywalking.apm.util.StringUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Correlation context, use to propagation user custom data.
 * Working on the protocol and delegate set/get method.
 */
public class CorrelationContext {

    private volatile Map<String, String> data;

    public CorrelationContext() {
        this.data = new HashMap<>(0);
    }

    public SettingResult set(String key, String value) {
        // key must not null
        if (key == null) {
            return SettingResult.buildWithSettingError("Key Cannot be null");
        }
        if (value == null) {
            value = "";
        }

        // check value length
        if (value.length() > Config.Correlation.VALUE_LENGTH) {
            return SettingResult.buildWithSettingError("Out out correlation value length limit");
        }

        // already contain key
        if (data.containsKey(key)) {
            final String previousValue = data.put(key, value);
            return SettingResult.buildWithSuccess(previousValue);
        }

        // check keys count
        if (data.size() >= Config.Correlation.KEY_COUNT) {
            return SettingResult.buildWithSettingError("Out out correlation key count limit");
        }

        // setting
        data.put(key, value);
        return SettingResult.buildWithSuccess(null);
    }

    public String get(String key) {
        if (key == null) {
            return "";
        }

        final String value = data.get(key);
        return value == null ? "" : value;
    }

    /**
     * Serialize this {@link CorrelationContext} to a {@link String}
     *
     * @return the serialization string.
     */
    String serialize() {
        if (data.isEmpty()) {
            return "";
        }

        return data.entrySet().stream()
            .map(entry -> Base64.encode(entry.getKey()) + ":" + Base64.encode(entry.getValue()))
            .collect(Collectors.joining(","));
    }

    /**
     * Deserialize data from {@link String}
     */
    void deserialize(String value) {
        if (StringUtil.isEmpty(value)) {
            return;
        }

        for (String perData : value.split(",")) {
            final String[] parts = perData.split(":");
            String perDataKey = parts[0];
            String perDataValue = parts.length > 1 ? parts[1] : "";
            data.put(Base64.decode2UTFString(perDataKey), Base64.decode2UTFString(perDataValue));
        }
    }

    /**
     * Reset correlation context from other context
     */
    public void resetFrom(CorrelationContext context) {
        this.data = context.data;
    }

    public static class SettingResult {
        private String errorMessage;
        private String previous;

        SettingResult(String errorMessage, String previous) {
            this.errorMessage = errorMessage;
            this.previous = previous;
        }

        /**
         * Building setting success
         *
         * @param previous previous value when override
         */
        public static SettingResult buildWithSuccess(String previous) {
            return new SettingResult(null, previous);
        }

        /**
         * Building setting error
         */
        public static SettingResult buildWithSettingError(String errorMessage) {
            return new SettingResult(errorMessage, null);
        }

        public String errorMessage() {
            return errorMessage;
        }

        public String previousData() {
            return previous;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CorrelationContext that = (CorrelationContext) o;
        return Objects.equals(data, that.data);
    }

    @Override
    public int hashCode() {
        return Objects.hash(data);
    }
}
