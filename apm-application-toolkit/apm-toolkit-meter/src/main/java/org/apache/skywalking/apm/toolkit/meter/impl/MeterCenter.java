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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Management all the meter.
 */
public class MeterCenter {

    private static final Map<MeterId, BaseMeter> METER_MAP = new ConcurrentHashMap<>();

    /**
     * Get or create a new meter
     * @return If already exists, it will return existed meter, otherwise it will register it.
     */
    public static <BUILDER extends BaseBuilder, BASE extends BaseMeter, IMPL extends AbstractMeter> BASE getOrCreateMeter(AbstractBuilder<BUILDER, BASE, IMPL> builder) {
        if (builder == null) {
            return null;
        }
        return (BASE) METER_MAP.compute(builder.getMeterId(), (meterId, previous) -> {
            if (previous == null) {
                return builder.create(meterId);
            }

            // Check previous meter is accept the new meter
            builder.accept((IMPL) previous);

            return previous;
        });
    }

    /**
     * Remove meter
     * @return Meter reference if exists
     */
    public static BaseMeter removeMeter(MeterId id) {
        if (id == null) {
            return null;
        }
        return METER_MAP.remove(id);
    }
}
