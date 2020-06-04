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

import org.apache.skywalking.apm.agent.core.meter.MeterId;
import org.apache.skywalking.apm.agent.core.meter.adapter.PercentileAdapter;
import org.apache.skywalking.apm.toolkit.activation.meter.util.MeterIdConverter;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public class ToolkitPercentileAdapter implements PercentileAdapter {

    private final Percentile percentile;
    private final MeterId id;

    public ToolkitPercentileAdapter(Percentile percentile) {
        this.percentile = percentile;
        this.id = MeterIdConverter.convert(percentile.getMeterId());
    }

    @Override
    public Map<Double, Long> drainRecords() {
        final HashMap<Double, Long> result = new HashMap<>();
        for (Map.Entry<Double, AtomicLong> entry : percentile.getRecordWithCount().entrySet()) {
            result.put(entry.getKey(), entry.getValue().getAndSet(0));
        }
        return result;
    }

    @Override
    public MeterId getId() {
        return id;
    }
}
