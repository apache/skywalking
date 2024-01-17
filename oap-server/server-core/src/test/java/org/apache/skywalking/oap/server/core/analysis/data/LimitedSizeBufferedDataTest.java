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

package org.apache.skywalking.oap.server.core.analysis.data;

import org.apache.skywalking.oap.server.core.analysis.topn.TopN;
import org.apache.skywalking.oap.server.core.storage.StorageID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class LimitedSizeBufferedDataTest {
    @Test
    public void testPut() {
        LimitedSizeBufferedData<MockStorageData> collection = new LimitedSizeBufferedData<>(5);
        collection.accept(new MockStorageData(1, 202401161130L));
        collection.accept(new MockStorageData(3, 202401161130L));
        collection.accept(new MockStorageData(5, 202401161130L));
        collection.accept(new MockStorageData(7, 202401161130L));
        collection.accept(new MockStorageData(9, 202401161130L));

        MockStorageData income = new MockStorageData(4, 202401161130L);
        MockStorageData incomeWithDifferentTimeBucket = new MockStorageData(4, 202401161131L);

        collection.accept(income);
        collection.accept(incomeWithDifferentTimeBucket);
        int[] expected = new int[] {
            4,
            3,
            4,
            5,
            7,
            9
        };
        int i = 0;
        for (MockStorageData data : collection.read()) {
            Assertions.assertEquals(expected[i++], data.latency);
        }
    }

    private class MockStorageData extends TopN {
        private long latency;
        private long timeBucket;

        public MockStorageData(long latency, long timeBucket) {
            this.latency = latency;
            this.timeBucket = timeBucket;
        }

        @Override
        public int compareTo(Object o) {
            MockStorageData target = (MockStorageData) o;
            return (int) (latency - target.latency);
        }

        @Override
        public StorageID id() {
            return new StorageID().append("ID", "id");
        }

        @Override
        public long getLatency() {
            return this.latency;
        }

        @Override
        public String getEntityId() {
            return "dbtest";
        }

        @Override
        public long getTimeBucket() {
            return this.timeBucket;
        }
    }
}
