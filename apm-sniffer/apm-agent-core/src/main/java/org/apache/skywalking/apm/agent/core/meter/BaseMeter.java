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

import java.util.List;
import java.util.Objects;
import org.apache.skywalking.apm.network.language.agent.v3.Label;
import org.apache.skywalking.apm.network.language.agent.v3.MeterData;

/**
 * BaseMeter is the basic class of all available meter implementations.
 * It includes all labels and unique id representing this meter.
 */
public abstract class BaseMeter {
    protected final MeterId meterId;

    public BaseMeter(MeterId meterId) {
        this.meterId = meterId;
    }

    /**
     * Get meter name
     */
    public String getName() {
        return meterId.getName();
    }

    /**
     * Get tag value
     */
    public String getTag(String tagKey) {
        for (MeterTag tag : meterId.getTags()) {
            if (tag.getKey().equals(tagKey)) {
                return tag.getValue();
            }
        }
        return null;
    }

    /**
     * Transform the meter to gRPC message bean
     * @return if dont need to transform or no changed, return null to ignore
     */
    public abstract MeterData.Builder transform();

    /**
     * Transform all tags to gRPC message
     */
    public List<Label> transformTags() {
        return getId().transformTags();
    }

    public MeterId getId() {
        return meterId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BaseMeter baseMeter = (BaseMeter) o;
        return Objects.equals(meterId, baseMeter.meterId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(meterId);
    }

}
