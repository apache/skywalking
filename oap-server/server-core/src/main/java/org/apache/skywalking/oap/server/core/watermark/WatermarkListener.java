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

package org.apache.skywalking.oap.server.core.watermark;

import java.util.List;
import lombok.Getter;

/**
 * WatermarkListener is the listener for receiving WatermarkEvent and react to it.
 * The implementations of this listener has two ways to interact with the WatermarkEvent:
 * 1. use {@link #isWatermarkExceeded()} == true to check if the watermark is exceeded.
 * 2. override {@link #beAwareOf(WatermarkEvent.Type)} to react to the event.
 *
 * When the oap recovered from the limiting state, the listener has two ways to be aware of it:
 * 1. use {@link #isWatermarkExceeded()} == false to check if the watermark is recovered.
 * 2. Be notified by calling {@link #beAwareOfRecovery()}}.
 */
public abstract class WatermarkListener {
    @Getter
    private String name;
    private List<WatermarkEvent.Type> acceptedTypes;
    private volatile boolean isWatermarkExceeded = false;

    /**
     * Create a listener that accepts all types of WatermarkEvent.
     * This should be the default way to create a listener.
     */
    public WatermarkListener(String name) {
        this(name, WatermarkEvent.Type.values());
    }

    public WatermarkListener(String name, WatermarkEvent.Type... types) {
        this.acceptedTypes = List.of(types);
    }

    boolean notify(WatermarkEvent.Type event) {
        if (acceptedTypes.contains(event)) {
            isWatermarkExceeded = true;
            beAwareOf(event);
            return true;
        }
        return false;
    }

    public boolean isWatermarkExceeded() {
        return isWatermarkExceeded;
    }

    /**
     * Receive the WatermarkEvent and react to it.
     */
    protected void beAwareOf(WatermarkEvent.Type event) {
    }

    /**
     * Receive the recovery status and react to it.
     */
    protected void beAwareOfRecovery() {
        isWatermarkExceeded = false;
    }
}
