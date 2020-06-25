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

package org.apache.skywalking.apm.agent.core.meter.transform;

import org.apache.skywalking.apm.agent.core.meter.MeterId;
import org.apache.skywalking.apm.agent.core.meter.adapter.MeterAdapter;
import org.apache.skywalking.apm.network.language.agent.v3.Label;
import org.apache.skywalking.apm.network.language.agent.v3.MeterData;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Meter transformer base data
 *
 * @see CounterTransformer
 * @see GaugeTransformer
 * @see HistogramTransformer
 */
public abstract class MeterTransformer<T extends MeterAdapter> {

    protected T adapter;

    // cache the gRPC label message
    private List<Label> labels;

    public MeterTransformer(T adapter) {
        this.adapter = adapter;
    }

    /**
     * Identity the meter
     */
    public MeterId getId() {
        return adapter.getId();
    }

    /**
     * Get meter name
     */
    public String getName() {
        return getId().getName();
    }

    /**
     * Transform all tags to gRPC message
     */
    public List<Label> transformTags() {
        if (labels != null) {
            return labels;
        }

        return labels = getId().getTags().stream()
            .map(t -> Label.newBuilder().setName(t.getKey()).setValue(t.getValue()).build())
            .collect(Collectors.toList());
    }

    /**
     * Transform the meter to gRPC message bean
     * @return if dont need to transform or no changed, return null to ignore
     */
    public abstract MeterData.Builder transform();

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MeterTransformer meterTransformer = (MeterTransformer) o;
        return Objects.equals(getId(), meterTransformer.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId());
    }
}
