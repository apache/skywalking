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

/**
 * WatermarkListener is the listener for receiving WatermarkEvent and react to it.
 */
public abstract class WatermarkListener {
    private List<WatermarkEvent.Type> acceptedTypes;
    private volatile boolean isWatermarkExceeded = false;

    public WatermarkListener() {
        this(WatermarkEvent.Type.values());
    }

    public WatermarkListener(WatermarkEvent.Type... types) {
        this.acceptedTypes = List.of(types);
    }

    void notify(WatermarkEvent.Type event) {
        if (acceptedTypes.contains(event)) {
            isWatermarkExceeded = true;
        }
    }

    void recovered() {
        isWatermarkExceeded = false;
    }

    public boolean isWatermarkExceeded() {
        return isWatermarkExceeded;
    }
}
