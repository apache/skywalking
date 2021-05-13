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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.skywalking.apm.agent.core.base64.Base64;
import org.apache.skywalking.apm.agent.core.conf.Config;
import org.apache.skywalking.apm.agent.core.context.tag.StringTag;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.util.StringUtil;

/**
 * Correlation context, use to propagation user custom data.
 */
public class CorrelationContext {

    private final Map<String, String> data;

    private static final List<String> AUTO_TAG_KEYS;

    static {
        if (StringUtil.isNotEmpty(Config.Correlation.AUTO_TAG_KEYS)) {
            AUTO_TAG_KEYS = Arrays.asList(Config.Correlation.AUTO_TAG_KEYS.split(","));
        } else {
            AUTO_TAG_KEYS = new ArrayList<>();
        }
    }

    public CorrelationContext() {
        this.data = new HashMap<>(Config.Correlation.ELEMENT_MAX_NUMBER);
    }

    /**
     * Add or override the context.
     *
     * @param key   to add or locate the existing context
     * @param value as new value
     * @return old one if exist.
     */
    public Optional<String> put(String key, String value) {
        // key must not null
        if (key == null) {
            return Optional.empty();
        }

        // remove and return previous value when value is empty
        if (StringUtil.isEmpty(value)) {
            return Optional.ofNullable(data.remove(key));
        }

        // check value length
        if (value.length() > Config.Correlation.VALUE_MAX_LENGTH) {
            return Optional.empty();
        }

        // already contain key
        if (data.containsKey(key)) {
            final String previousValue = data.put(key, value);
            return Optional.of(previousValue);
        }

        // check keys count
        if (data.size() >= Config.Correlation.ELEMENT_MAX_NUMBER) {
            return Optional.empty();
        }
        if (AUTO_TAG_KEYS.contains(key) && ContextManager.isActive()) {
            ContextManager.activeSpan().tag(new StringTag(key), value);
        }
        // setting
        data.put(key, value);
        return Optional.empty();
    }

    /**
     * @param key to find the context
     * @return value if exist.
     */
    public Optional<String> get(String key) {
        if (key == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(data.get(key));
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
            // Only data with limited count of elements can be added
            if (data.size() >= Config.Correlation.ELEMENT_MAX_NUMBER) {
                break;
            }
            final String[] parts = perData.split(":");
            if (parts.length != 2) {
                continue;
            }
            data.put(Base64.decode2UTFString(parts[0]), Base64.decode2UTFString(parts[1]));
        }
    }

    /**
     * Prepare for the cross-process propagation. Inject the {@link #data} into {@link
     * ContextCarrier#getCorrelationContext()}
     */
    void inject(ContextCarrier carrier) {
        carrier.getCorrelationContext().data.putAll(this.data);
    }

    /**
     * Extra the {@link ContextCarrier#getCorrelationContext()} into this context.
     */
    void extract(ContextCarrier carrier) {
        final Map<String, String> carrierCorrelationContext = carrier.getCorrelationContext().data;
        for (Map.Entry<String, String> entry : carrierCorrelationContext.entrySet()) {
            // Only data with limited count of elements can be added
            if (data.size() >= Config.Correlation.ELEMENT_MAX_NUMBER) {
                break;
            }

            this.data.put(entry.getKey(), entry.getValue());
        }
    }

    /**
     * Process the active span
     *
     * 1. Inject the tags with auto-tag flag into the span
     */
    void handle(AbstractSpan span) {
        AUTO_TAG_KEYS.forEach(key -> this.get(key).ifPresent(val -> span.tag(new StringTag(key), val)));
    }

    /**
     * Clone the context data, work for capture to cross-thread.
     */
    @Override
    public CorrelationContext clone() {
        final CorrelationContext context = new CorrelationContext();
        context.data.putAll(this.data);
        return context;
    }

    /**
     * Continue the correlation context in another thread.
     *
     * @param snapshot holds the context.
     */
    void continued(ContextSnapshot snapshot) {
        this.data.putAll(snapshot.getCorrelationContext().data);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        CorrelationContext that = (CorrelationContext) o;
        return Objects.equals(data, that.data);
    }

    @Override
    public int hashCode() {
        return Objects.hash(data);
    }
}
