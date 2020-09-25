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

import org.apache.skywalking.apm.agent.core.boot.ServiceManager;

import java.util.ArrayList;

/**
 * Help to build the meter
 */
public abstract class AbstractBuilder<BUILDER extends AbstractBuilder, METER extends BaseMeter> {

    private static MeterService METER_SERVICE;
    protected final MeterId meterId;

    /**
     * Build a new meter build, meter name is required
     */
    public AbstractBuilder(String name) {
        if (name == null) {
            throw new IllegalArgumentException("Meter name cannot be null");
        }
        this.meterId = new MeterId(name, getType(), new ArrayList<>());
    }

    /**
     * append new tag to this meter
     */
    public BUILDER tag(String name, String value) {
        meterId.getTags().add(new MeterTag(name, value));
        return (BUILDER) this;
    }

    /**
     * Get supported build meter type
     */
    protected abstract MeterType getType();

    /**
     * Create a meter adapter
     */
    protected abstract METER create(MeterId meterId);

    /**
     * Build a new meter object
     */
    public METER build() {
        // sort the tags
        this.meterId.getTags().sort(MeterTag::compareTo);
        // create or get the meter
        if (METER_SERVICE == null) {
            METER_SERVICE = ServiceManager.INSTANCE.findService(MeterService.class);
        }
        final METER adapter = this.create(meterId);

        METER_SERVICE.register(adapter);

        return (METER) adapter;
    }
}