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
 */

package org.apache.skywalking.oap.server.core.analysis;

public enum DownSampling {
    /**
     * None downsampling is for un-time-series data.
     */
    None(0, ""),
    /**
     * Second downsampling is not for metrics, but for record, profile and top n. Those are details but don't do
     * aggregation, and still merge into day level in the persistence.
     */
    Second(1, "second"),
    Minute(2, "minute"),
    Hour(3, "hour"),
    Day(4, "day");

    private final int value;
    private final String name;

    DownSampling(int value, String name) {
        this.value = value;
        this.name = name;
    }

    public int getValue() {
        return value;
    }

    public String getName() {
        return name;
    }
}
