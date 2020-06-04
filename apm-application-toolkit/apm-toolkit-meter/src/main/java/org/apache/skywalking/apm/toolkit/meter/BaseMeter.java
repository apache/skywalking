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

import java.util.Objects;

/**
 * Base meter bean, contain meter id and base builder. Extend this and implement the builder to build a new meter.
 */
public abstract class BaseMeter {

    protected final MeterId meterId;

    public BaseMeter(MeterId meterId) {
        this.meterId = meterId;
    }

    /**
     * Get meter name
     */
    public String getName() {
        return meterId.getName();
    }

    /**
     * Get tag value
     */
    public String getTag(String tagKey) {
        for (MeterId.Tag tag : meterId.getTags()) {
            if (tag.getName().equals(tagKey)) {
                return tag.getValue();
            }
        }
        return null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BaseMeter baseMeter = (BaseMeter) o;
        return Objects.equals(meterId, baseMeter.meterId);
    }

    /**
     * Get meter id
     */
    public MeterId getMeterId() {
        return meterId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(meterId);
    }

    public static abstract class Builder<T extends BaseMeter> {

        protected final MeterId meterId;

        /**
         * Build a new meter build, meter name is required
         */
        public Builder(String name) {
            if (name == null) {
                throw new IllegalArgumentException("Meter name cannot be null");
            }
            this.meterId = new MeterId(name, getType());
        }

        /**
         * Build a new meter build from exists meter id
         */
        public Builder(MeterId meterId) {
            if (meterId == null) {
                throw new IllegalArgumentException("Meter id cannot be null");
            }
            this.meterId = meterId;
        }

        /**
         * append new tag
         */
        public Builder<T> tag(String name, String value) {
            meterId.getTags().add(new MeterId.Tag(name, value));
            return this;
        }

        /**
         * Accept the new meter when register, could use it to judge histogram buckets is correct.
         * It's working on the same meter id only.
         * @throws IllegalArgumentException if cannot be accept, throws information
         */
        protected void accept(T meter) throws IllegalArgumentException {
        }

        /**
         * Create a meter
         */
        protected abstract T create(MeterId meterId);

        /**
         * Get supported build meter type
         */
        protected abstract MeterId.MeterType getType();

        /**
         * Get current meter id
         */
        public MeterId getMeterId() {
            return meterId;
        }

        /**
         * Build a new meter object
         */
        public T build() {
            // sort the tags
            this.meterId.getTags().sort(MeterId.Tag::compareTo);
            // create or get the meter
            return MeterCenter.getOrCreateMeter(this);
        }
    }
}
