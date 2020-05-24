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

import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.network.language.agent.v3.MeterData;
import org.apache.skywalking.apm.network.language.agent.v3.MeterSingleValue;

import java.util.function.Supplier;

public class Gauge extends Meter<Gauge> {
    private static final ILog logger = LogManager.getLogger(Gauge.class);

    private final Supplier<Long> getter;

    public Gauge(MeterId id, Supplier<Long> getter) {
        super(id);
        this.getter = getter;
    }

    /**
     * Get the value
     */
    public Long get() {
        return getter.get();
    }

    @Override
    public boolean accept(Gauge newMeter) {
        // getter must be same
        return newMeter.getter == getter;
    }

    @Override
    public MeterData.Builder transform() {
        if (getter == null) {
            return null;
        }

        // get count
        Long count;
        try {
            count = getter.get();
        } catch (Exception e) {
            logger.warn(e, "Cannot get the count in meter:{}", id.getName());
            return null;
        }

        if (count == null) {
            return null;
        }

        final MeterData.Builder builder = MeterData.newBuilder();
        builder.setSingleValue(MeterSingleValue.newBuilder()
            .setName(id.getName())
            .addAllLabels(id.transformTags())
            .setValue(count).build());

        return builder;
    }
}
