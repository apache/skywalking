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

import java.util.Objects;

/**
 * Meter base data, will register to {@link MeterRegistryService}
 *
 * @see Counter
 * @see Gauge
 */
public abstract class Meter<T extends Meter> {

    protected MeterId id;

    public Meter(MeterId id) {
        this.id = id;
    }

    /**
     * Identity the meter
     */
    public MeterId getId() {
        return id;
    }

    /**
     * Transform the meter to gRPC message bean
     * @return if dont need to transform or no changed, return null to ignore
     */
    public abstract MeterData.Builder transform();

    /**
     * Accept the new meter when register, could use it to judge histogram buckets is correct.
     * It's working on the same meter id only.
     * @return if accept the new meter, return true, otherwise return false
     */
    public boolean accept(T newMeter) {
        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Meter meter = (Meter) o;
        return Objects.equals(id, meter.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
