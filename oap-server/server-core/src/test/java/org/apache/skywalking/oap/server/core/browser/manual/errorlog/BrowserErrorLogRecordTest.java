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

package org.apache.skywalking.oap.server.core.browser.manual.errorlog;

import org.apache.skywalking.oap.server.core.storage.StorageID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class BrowserErrorLogRecordTest {

    /**
     * Test that records with the same uniqueId but different timestamps produce
     * different storage IDs.
     * This is the core requirement to prevent duplicate UUID issues in BanyanDB.
     */
    @Test
    public void testDuplicateUniqueIdWithDifferentTimestampsProduceUniqueIds() {
        // Create first record with a specific uniqueId and timestamp
        BrowserErrorLogRecord record1 = new BrowserErrorLogRecord();
        record1.setUniqueId("55ec6178-3fb7-43ef-899c-a26944407b01");
        record1.setTimestamp(1704672000000L); // 2024-01-08 00:00:00.000

        // Create second record with the SAME uniqueId but DIFFERENT timestamp
        BrowserErrorLogRecord record2 = new BrowserErrorLogRecord();
        record2.setUniqueId("55ec6178-3fb7-43ef-899c-a26944407b01"); // Same UUID
        record2.setTimestamp(1704672000001L); // 2024-01-08 00:00:00.001 (1ms later)

        // Get the storage IDs
        StorageID id1 = record1.id();
        StorageID id2 = record2.id();

        // Assert that the IDs are different
        Assertions.assertNotEquals(id1, id2,
                "Records with duplicate uniqueId but different timestamps should have different storage IDs");

        // Assert the built string IDs are also different
        Assertions.assertNotEquals(id1.build(), id2.build(),
                "Built storage ID strings should be different for duplicate uniqueId with different timestamps");
    }

    /**
     * Test that records with the same uniqueId AND same timestamp produce identical
     * storage IDs.
     * This ensures ID generation is deterministic.
     */
    @Test
    public void testSameUniqueIdAndTimestampProduceSameId() {
        BrowserErrorLogRecord record1 = new BrowserErrorLogRecord();
        record1.setUniqueId("55ec6178-3fb7-43ef-899c-a26944407b02");
        record1.setTimestamp(1704672000000L);

        BrowserErrorLogRecord record2 = new BrowserErrorLogRecord();
        record2.setUniqueId("55ec6178-3fb7-43ef-899c-a26944407b02");
        record2.setTimestamp(1704672000000L);

        StorageID id1 = record1.id();
        StorageID id2 = record2.id();

        // Assert that the IDs are equal
        Assertions.assertEquals(id1, id2,
                "Records with same uniqueId and timestamp should have identical storage IDs");

        Assertions.assertEquals(id1.build(), id2.build(),
                "Built storage ID strings should be identical for same uniqueId and timestamp");
    }

    /**
     * Test that the storage ID format follows the expected pattern:
     * {uniqueId}_{timestamp}
     */
    @Test
    public void testStorageIdFormat() {
        BrowserErrorLogRecord record = new BrowserErrorLogRecord();
        String uniqueId = "test-uuid-123";
        long timestamp = 1704672000000L;

        record.setUniqueId(uniqueId);
        record.setTimestamp(timestamp);

        String storageId = record.id().build();

        // Assert the format is correct
        Assertions.assertEquals(
                uniqueId + "_" + timestamp,
                storageId,
                "Storage ID should follow the format {uniqueId}_{timestamp}");
    }

    /**
     * Test that different uniqueIds with different timestamps produce different IDs
     * (basic uniqueness).
     */
    @Test
    public void testDifferentUniqueIdsProduceDifferentIds() {
        BrowserErrorLogRecord record1 = new BrowserErrorLogRecord();
        record1.setUniqueId("uuid-1");
        record1.setTimestamp(1704672000000L);

        BrowserErrorLogRecord record2 = new BrowserErrorLogRecord();
        record2.setUniqueId("uuid-2");
        record2.setTimestamp(1704672000001L);

        Assertions.assertNotEquals(record1.id(), record2.id(),
                "Records with different uniqueIds should have different storage IDs");
    }

    /**
     * Test that the same uniqueId with significantly different timestamps produces
     * different IDs.
     * This tests the scenario where browser-provided UUIDs collide across different
     * time windows.
     */
    @Test
    public void testDuplicateUniqueIdWithLargeTimestampDifferenceProduceUniqueIds() {
        BrowserErrorLogRecord record1 = new BrowserErrorLogRecord();
        record1.setUniqueId("browser-uuid-collision");
        record1.setTimestamp(1704672000000L); // 2024-01-08 00:00:00

        BrowserErrorLogRecord record2 = new BrowserErrorLogRecord();
        record2.setUniqueId("browser-uuid-collision"); // Same UUID (browser limitation)
        record2.setTimestamp(1704758400000L); // 2024-01-09 00:00:00 (24 hours later)

        StorageID id1 = record1.id();
        StorageID id2 = record2.id();

        Assertions.assertNotEquals(id1, id2,
                "Records with duplicate uniqueId from different time periods should have different storage IDs");
    }
}
