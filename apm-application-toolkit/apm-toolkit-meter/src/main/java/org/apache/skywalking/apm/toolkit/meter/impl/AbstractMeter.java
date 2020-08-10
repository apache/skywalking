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

import org.apache.skywalking.apm.toolkit.meter.MeterId;

import java.util.Objects;

/**
 * Base meter implementation bean
 */
public abstract class AbstractMeter {

    protected final MeterId meterId;

    public AbstractMeter(MeterId meterId) {
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
        for (MeterId.Tag tag : meterId.getTags()) {
            if (tag.getName().equals(tagKey)) {
                return tag.getValue();
            }
        }
        return null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AbstractMeter abstractMeter = (AbstractMeter) o;
        return Objects.equals(meterId, abstractMeter.meterId);
    }

    public MeterId getMeterId() {
        return meterId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(meterId);
    }

}
