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
import java.util.function.Supplier;

public class Gauge extends BaseMeter {

    private Supplier<Long> getter;

    public static Builder create(String name, Supplier<Long> getter) {
        return new Builder(name, getter);
    }

    private Gauge(String name, List<Tag> tags, Supplier<Long> getter) {
        super(name, tags);
        this.getter = getter;
    }

    /**
     * Get count
     */
    public Long get() {
        return getter.get();
    }

    /**
     * Build the gauge
     */
    public static class Builder {
        private final String name;
        private final Supplier<Long> getter;
        private List<Tag> tags = new ArrayList<>();

        public Builder(String name, Supplier<Long> getter) {
            if (getter == null) {
                throw new NullPointerException("getter cannot be null");
            }

            this.name = name;
            this.getter = getter;
        }

        /**
         * append new tag
         */
        public Gauge.Builder tag(String name, String value) {
            this.tags.add(new Tag(name, value));
            return this;
        }

        /**
         * Build a new gauge object
         * @return
         */
        public Gauge build() {
            return new Gauge(name, tags, getter);
        }
    }
}
