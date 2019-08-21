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

import java.util.Objects;
import org.apache.skywalking.oap.server.core.storage.ComparableStorageData;
import org.junit.*;

/**
 * @author wusheng
 */
public class LimitedSizeDataCollectionTest {
    @Test
    public void testPut() {
        LimitedSizeDataCollection<MockStorageData> collection = new LimitedSizeDataCollection<>(5);
        collection.put(new MockStorageData(1));
        collection.put(new MockStorageData(3));
        collection.put(new MockStorageData(5));
        collection.put(new MockStorageData(7));
        collection.put(new MockStorageData(9));

        MockStorageData income = new MockStorageData(4);
        collection.put(income);

        int[] expected = new int[] {3, 4, 5, 7, 9};
        int i = 0;
        for (MockStorageData data : collection.collection()) {
            Assert.assertEquals(expected[i++], data.latency);
        }
    }

    private class MockStorageData implements ComparableStorageData {
        private long latency;

        public MockStorageData(long latency) {
            this.latency = latency;
        }

        @Override public int compareTo(Object o) {
            MockStorageData target = (MockStorageData)o;
            return (int)(latency - target.latency);
        }

        @Override public String id() {
            return null;
        }

        @Override public boolean equals(Object o) {
            return true;
        }

        @Override public int hashCode() {
            return Objects.hash(1);
        }
    }
}
