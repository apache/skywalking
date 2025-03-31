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

package org.apache.skywalking.oap.server.core.storage.ttl;

import lombok.Data;

/**
 * Metrics TTL includes the definition of the TTL of the metrics-ish data in the storage,
 * e.g.
 * 1. The metadata of the service, instance, endpoint, topology map, etc.
 * 2. Generated metrics data from OAL and MAL engines.
 *
 * TTLs for ach granularity metrics are listed separately.
 */
@Data
public class MetricsTTL {
    private final int minute;
    private final int hour;
    private final int day;
    // -1 means no cold stage.
    private int coldMinute = -1;
    private int coldHour = -1;
    private int coldDay = -1;
}
