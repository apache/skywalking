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

package org.apache.skywalking.oal.v2.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.Getter;

/**
 * Immutable representation of a source reference in OAL.
 *
 * Examples:
 * <pre>
 * from(Service.latency)           → name="Service", attribute="latency"
 * from(Service.*)                 → name="Service", attribute=wildcard
 * from((long)Service.tag["key"])  → name="Service", attribute="tag[key]", castType="long"
 * </pre>
 */
@Getter
public final class SourceReference {
    private final String name;
    private final List<String> attributes;
    private final Optional<String> castType;
    private final boolean wildcard;

    private SourceReference(Builder builder) {
        this.name = Objects.requireNonNull(builder.name, "source name cannot be null");
        this.attributes = Collections.unmodifiableList(new ArrayList<>(builder.attributes));
        this.castType = Optional.ofNullable(builder.castType);
        this.wildcard = builder.wildcard;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static SourceReference of(String name) {
        return builder().name(name).build();
    }

    public static SourceReference of(String name, String attribute) {
        return builder().name(name).addAttribute(attribute).build();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SourceReference that = (SourceReference) o;
        return wildcard == that.wildcard &&
            Objects.equals(name, that.name) &&
            Objects.equals(attributes, that.attributes) &&
            Objects.equals(castType, that.castType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, attributes, castType, wildcard);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (castType.isPresent()) {
            sb.append("(").append(castType.get()).append(")");
        }
        sb.append(name);
        if (wildcard) {
            sb.append(".*");
        } else {
            for (String attr : attributes) {
                sb.append(".").append(attr);
            }
        }
        return sb.toString();
    }

    public static class Builder {
        private String name;
        private List<String> attributes = new ArrayList<>();
        private String castType;
        private boolean wildcard;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder addAttribute(String attribute) {
            this.attributes.add(attribute);
            return this;
        }

        public Builder attributes(List<String> attributes) {
            this.attributes = new ArrayList<>(attributes);
            return this;
        }

        public Builder castType(String castType) {
            this.castType = castType;
            return this;
        }

        public Builder wildcard(boolean wildcard) {
            this.wildcard = wildcard;
            return this;
        }

        public SourceReference build() {
            return new SourceReference(this);
        }
    }
}
