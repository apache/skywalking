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

package org.apache.skywalking.apm.toolkit.meter;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Meter identity
 */
public class MeterId {

    protected final String name;
    protected final MeterType type;
    protected final List<Tag> tags = new ArrayList<>();

    public MeterId(String name, MeterType type) {
        this.name = name;
        this.type = type;
    }

    public MeterId(String name, MeterType type, List<Tag> tags) {
        this.name = name;
        this.type = type;
        this.tags.addAll(tags);
    }

    public String getName() {
        return name;
    }

    public MeterType getType() {
        return type;
    }

    public List<Tag> getTags() {
        return tags;
    }

    /**
     * Simple copy to a new meter id, change the name and type
     */
    public MeterId copyTo(String name, MeterType type) {
        return new MeterId(name, type, tags);
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

    /**
     * The meter type
     */
    public enum MeterType {
        COUNTER,
        GAUGE,
        HISTOGRAM
    }

    /**
     * Using name/value pair as the tag, also it will {@link Comparable} when we sort all of tags
     */
    public static class Tag implements Comparable<Tag> {
        private String name;
        private String value;

        public Tag(String name, String value) {
            this.name = name;
            this.value = value;
        }

        public String getName() {
            return name;
        }

        public String getValue() {
            return value;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Tag tag = (Tag) o;
            return Objects.equals(name, tag.name) &&
                Objects.equals(value, tag.value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, value);
        }

        @Override
        public int compareTo(Tag o) {
            return this.name.compareTo(o.name);
        }
    }

}
