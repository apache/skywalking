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

package org.apache.skywalking.apm.toolkit.meter.impl;

import org.apache.skywalking.apm.toolkit.meter.BaseBuilder;
import org.apache.skywalking.apm.toolkit.meter.BaseMeter;
import org.apache.skywalking.apm.toolkit.meter.MeterId;

import java.util.Objects;

/**
 * Help to build the meter
 */
public abstract class AbstractBuilder<BUILDER extends BaseBuilder, BASE extends BaseMeter, IMPL extends AbstractMeter> implements BaseBuilder<BUILDER, BASE> {
    protected final MeterId meterId;

    /**
     * Build a new meter build, meter name is required
     */
    public AbstractBuilder(String name) {
        if (name == null) {
            throw new IllegalArgumentException("Meter name cannot be null");
        }
        this.meterId = new MeterId(name, getType());
    }

    /**
     * Build a new meter build from exists meter id
     */
    public AbstractBuilder(MeterId meterId) {
        if (meterId == null) {
            throw new IllegalArgumentException("Meter id cannot be null");
        }
        if (!Objects.equals(meterId.getType(), getType())) {
            throw new IllegalArgumentException("Meter id type is not matches");
        }
        this.meterId = meterId;
    }

    /**
     * append new tag to this meter
     */
    public BUILDER tag(String name, String value) {
        meterId.getTags().add(new MeterId.Tag(name, value));
        return (BUILDER) this;
    }

    /**
     * Create a meter
     */
    protected abstract BASE create(MeterId meterId);

    /**
     * Accept the new meter when register, could use it to judge histogram buckets is correct.
     * It's working on the same meter id only.
     * @throws IllegalArgumentException if cannot be accept, throws information
     */
    protected void accept(IMPL meter) throws IllegalArgumentException {
    }

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
    public BASE build() {
        // sort the tags
        this.meterId.getTags().sort(MeterId.Tag::compareTo);
        // create or get the meter
        return MeterCenter.getOrCreateMeter(this);
    }
}
