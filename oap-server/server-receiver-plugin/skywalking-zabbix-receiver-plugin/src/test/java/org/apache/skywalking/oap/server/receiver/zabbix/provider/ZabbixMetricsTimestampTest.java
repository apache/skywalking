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

package org.apache.skywalking.oap.server.receiver.zabbix.provider;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ZabbixMetricsTimestampTest {

    @Test
    public void sampleTimeKeepsMillisecondsFromNs() {
        // clock=1609588563, ns=87682907 -> 87ms within that second. ns-only leftover is dropped.
        assertEquals(1_609_588_563_087L, ZabbixMetrics.sampleTimeMillis(1_609_588_563L, 87_682_907L));
        assertEquals(1_609_588_563_000L, ZabbixMetrics.sampleTimeMillis(1_609_588_563L, 0L));
        // Truncate toward zero at the millisecond boundaries. 999999999ns stays in the same second.
        assertEquals(1_609_588_563_000L, ZabbixMetrics.sampleTimeMillis(1_609_588_563L, 999_999L));
        assertEquals(1_609_588_563_001L, ZabbixMetrics.sampleTimeMillis(1_609_588_563L, 1_000_000L));
        assertEquals(1_609_588_563_999L, ZabbixMetrics.sampleTimeMillis(1_609_588_563L, 999_999_999L));
    }
}
