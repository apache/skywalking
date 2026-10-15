/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.skywalking.oap.server.core.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class K8sMetadataReadSettingsTest {
    @Test
    public void readsTheSizeOfEachLane() {
        assertArrayEquals(new int[] {1000, 200}, K8sMetadataReadSettings.parseReadQueueSizes("als=1000,mal=200"));
        assertArrayEquals(new int[] {1000, 200}, K8sMetadataReadSettings.parseReadQueueSizes(" mal = 200 , als=1000 "));
    }

    @Test
    public void aLaneLeftOutHasNoLimit() {
        assertArrayEquals(new int[] {1000, -1}, K8sMetadataReadSettings.parseReadQueueSizes("als=1000"));
        assertArrayEquals(new int[] {-1, 200}, K8sMetadataReadSettings.parseReadQueueSizes("mal=200"));
        assertArrayEquals(new int[] {-1, -1}, K8sMetadataReadSettings.parseReadQueueSizes(""));
        assertArrayEquals(new int[] {-1, -1}, K8sMetadataReadSettings.parseReadQueueSizes(null));
    }

    @Test
    public void readsTheCacheTimeOfEachLaneAndLeavesOutForTwentyOneMinutes() {
        assertArrayEquals(new int[] {30, 10}, K8sMetadataReadSettings.parseCacheTimes("als=30,mal=10", 3));
        assertArrayEquals(new int[] {30, 21}, K8sMetadataReadSettings.parseCacheTimes("als=30", 3));
        assertArrayEquals(new int[] {21, 21}, K8sMetadataReadSettings.parseCacheTimes("", 3));
    }

    @Test
    public void aCacheTimeIsLongerThanTheRefreshPeriod() {
        assertThrows(IllegalArgumentException.class, () -> K8sMetadataReadSettings.parseCacheTimes("als=3", 3));
        assertThrows(IllegalArgumentException.class, () -> K8sMetadataReadSettings.parseCacheTimes("", 21));
    }

    @Test
    public void publishesEverySetting() {
        try {
            K8sMetadataReadSettings.set(1234, "als=0,mal=1", "als=30,mal=10", 2, 5);
            assertEquals(1234, K8sMetadataReadSettings.getMalReadTimeoutMillis());
            assertEquals(0, K8sMetadataReadSettings.getAlsReadQueueSize());
            assertEquals(1, K8sMetadataReadSettings.getMalReadQueueSize());
            assertEquals(Duration.ofMinutes(30), K8sMetadataReadSettings.getAlsCacheTime());
            assertEquals(Duration.ofMinutes(10), K8sMetadataReadSettings.getMalCacheTime());
            assertEquals(Duration.ofMinutes(2), K8sMetadataReadSettings.getRefreshPeriod());
            assertEquals(Duration.ofMinutes(5), K8sMetadataReadSettings.getNotFoundRetryPeriod());
        } finally {
            K8sMetadataReadSettings.set(2000, "", "als=21,mal=21", 3, 1);
        }
    }

    @Test
    public void publishesNothingWhenASettingIsNotInItsForm() {
        assertThrows(IllegalArgumentException.class,
                     () -> K8sMetadataReadSettings.set(1234, "als=0", "als=2", 3, 1));
        assertEquals(2000, K8sMetadataReadSettings.getMalReadTimeoutMillis());
        assertEquals(-1, K8sMetadataReadSettings.getAlsReadQueueSize());
        assertThrows(IllegalArgumentException.class, () -> K8sMetadataReadSettings.set(2000, "", "", 0, 1));
        assertThrows(IllegalArgumentException.class, () -> K8sMetadataReadSettings.set(2000, "", "", 3, 0));
        assertThrows(IllegalArgumentException.class, () -> K8sMetadataReadSettings.set(-1, "", "", 3, 1));
    }

    @Test
    public void rejectsAnythingElse() {
        for (final String invalid : new String[] {"1000", "als=1000,", "ebpf=10", "als=ten", "als=1,als=2", "als"}) {
            assertThrows(IllegalArgumentException.class, () -> K8sMetadataReadSettings.parseReadQueueSizes(invalid),
                         invalid);
            assertThrows(IllegalArgumentException.class, () -> K8sMetadataReadSettings.parseCacheTimes(invalid, 3),
                         invalid);
        }
    }

    @Test
    public void rejectsABareNumberTheConfigLoaderTyped() {
        final IllegalArgumentException queueSize = assertThrows(
            IllegalArgumentException.class, () -> K8sMetadataReadSettings.parseReadQueueSizes(1000));
        assertTrue(queueSize.getMessage().contains("als=<size>,mal=<size>"), queueSize.getMessage());
        assertTrue(queueSize.getMessage().endsWith("but is: 1000"), queueSize.getMessage());
        final IllegalArgumentException cacheTime = assertThrows(
            IllegalArgumentException.class, () -> K8sMetadataReadSettings.parseCacheTimes(21, 3));
        assertTrue(cacheTime.getMessage().contains("als=<minutes>,mal=<minutes>"), cacheTime.getMessage());
    }
}
