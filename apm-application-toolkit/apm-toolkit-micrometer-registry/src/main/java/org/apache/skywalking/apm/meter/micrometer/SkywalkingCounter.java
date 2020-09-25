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

package org.apache.skywalking.apm.meter.micrometer;

import io.micrometer.core.instrument.AbstractMeter;
import org.apache.skywalking.apm.toolkit.meter.Counter;

/**
 * Wrapper the {@link Counter} to {@link io.micrometer.core.instrument.Counter}
 */
public class SkywalkingCounter extends AbstractMeter implements io.micrometer.core.instrument.Counter {
    private final Counter counter;

    SkywalkingCounter(Id id, Counter counter) {
        super(id);
        this.counter = counter;
    }

    @Override
    public void increment(double amount) {
        this.counter.increment(amount);
    }

    @Override
    public double count() {
        return counter.get();
    }

}
