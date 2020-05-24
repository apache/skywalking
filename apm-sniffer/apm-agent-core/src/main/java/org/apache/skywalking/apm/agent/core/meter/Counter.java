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

import org.apache.skywalking.apm.network.language.agent.v3.MeterData;
import org.apache.skywalking.apm.network.language.agent.v3.MeterSingleValue;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class Counter extends Meter<Counter> {

    private final AtomicLong count = new AtomicLong();
    private final AtomicReference<Long> previous = new AtomicReference();

    public Counter(MeterId id) {
        super(id);
    }

    /**
     * increment the value
     */
    public void increment(long count) {
        this.count.addAndGet(count);
    }

    @Override
    public MeterData.Builder transform() {
        final long currentValue = count.get();
        final Long previousValue = previous.getAndSet(currentValue);

        // calculate the add count
        long addCount;
        if (previousValue == null) {
            addCount = currentValue;
        } else {
            addCount = currentValue - previousValue;
        }

        if (addCount == 0) {
            return null;
        }

        final MeterData.Builder builder = MeterData.newBuilder();
        builder.setSingleValue(MeterSingleValue.newBuilder()
            .setName(id.getName())
            .addAllLabels(id.transformTags())
            .setValue(addCount).build());

        return builder;
    }

}
