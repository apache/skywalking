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

package org.apache.skywalking.library.banyandb.v1.client;

import java.util.Optional;
import lombok.Getter;
import org.apache.skywalking.banyandb.common.v1.BanyandbCommon;

public abstract class AbstractWrite<P extends com.google.protobuf.GeneratedMessageV3> {
    /**
     * Timestamp represents the time of the current data point, in milliseconds.
     * <p>
     * <b>When to set:</b>
     * <ul>
     *   <li><b>Stream and Measure writes:</b> This field <i>must</i> be set to indicate the event time.</li>
     *   <li><b>Trace writes:</b> This field is <i>not needed</i> and should be left unset; trace data does not require an explicit timestamp here.</li>
     * </ul>
     */
    @Getter
    protected Optional<Long> timestamp;

    @Getter
    protected final BanyandbCommon.Metadata entityMetadata;

    public AbstractWrite(BanyandbCommon.Metadata entityMetadata, long timestamp) {
        this(entityMetadata);
        this.timestamp = Optional.of(timestamp);
    }

    /**
     * Build a write request without initial timestamp.
     */
    AbstractWrite(BanyandbCommon.Metadata entityMetadata) {
        if (entityMetadata == null) {
            throw new IllegalArgumentException("metadata not found");
        }
        this.entityMetadata = entityMetadata;
    }

    public P build() {
        return build(entityMetadata);
    }

    public P buildOnlyValues() {
        return buildValues();
    }

    protected abstract P build(BanyandbCommon.Metadata metadata);

    protected abstract P buildValues();
}
