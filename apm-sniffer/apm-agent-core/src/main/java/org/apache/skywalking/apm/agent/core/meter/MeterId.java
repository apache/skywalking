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

package org.apache.skywalking.apm.agent.core.meter;

import org.apache.skywalking.apm.network.language.agent.v3.Label;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Identity the meter, including name, type and tags.
 */
public class MeterId {

    private final String name;
    private final MeterType type;
    private final List<MeterTag> tags;

    // Labels are used to report meter to the backend.
    private List<Label> labels;

    public MeterId(String name, MeterType type, List<MeterTag> tags) {
        this.name = name;
        this.type = type;
        this.tags = tags;
    }

    public String getName() {
        return name;
    }

    public List<MeterTag> getTags() {
        return tags;
    }

    public MeterType getType() {
        return type;
    }

    /**
     * transform tags to label message
     */
    public List<Label> transformTags() {
        if (labels != null) {
            return labels;
        }

        return labels = tags.stream()
            .map(t -> Label.newBuilder().setName(t.getKey()).setValue(t.getValue()).build())
            .collect(Collectors.toList());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MeterId meterId = (MeterId) o;
        return Objects.equals(name, meterId.name) &&
            type == meterId.type &&
            Objects.equals(tags, meterId.tags);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, type, tags);
    }
}
