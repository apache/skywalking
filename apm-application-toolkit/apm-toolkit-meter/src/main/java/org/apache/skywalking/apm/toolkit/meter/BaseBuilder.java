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
 * Help to build the meter
 */
public abstract class BaseBuilder<BUILDER extends BaseBuilder, METER extends BaseMeter> {
    protected final MeterId meterId;

    /**
     * Build a new meter build, meter name is required
     */
    public BaseBuilder(String name) {
        if (name == null) {
            throw new IllegalArgumentException("Meter name cannot be null");
        }
        this.meterId = new MeterId(name, getType());
    }

    /**
     * Build a new meter build from exists meter id
     */
    public BaseBuilder(MeterId meterId) {
        if (meterId == null) {
            throw new IllegalArgumentException("Meter id cannot be null");
        }
        if (!Objects.equals(meterId.getType(), getType())) {
            throw new IllegalArgumentException("Meter id type is not matches");
        }
        this.meterId = meterId;
    }

    /**
     * Get supported build meter type
     */
    protected abstract MeterId.MeterType getType();

    /**
     * Create a meter
     */
    protected abstract METER create();

    /**
     * append new tags to this meter
     */
    public BUILDER tag(String name, String value) {
        meterId.getTags().add(new MeterId.Tag(name, value));
        return (BUILDER) this;
    }

    /**
     * Build a new meter object
     */
    public METER build() {
        // sort the tags
        this.meterId.getTags().sort(MeterId.Tag::compareTo);
        // create or get the meter
        return create();
    }

}
