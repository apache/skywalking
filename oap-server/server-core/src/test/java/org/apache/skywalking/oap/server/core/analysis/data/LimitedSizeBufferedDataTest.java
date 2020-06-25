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
import org.junit.Assert;
import org.junit.Test;

public class LimitedSizeBufferedDataTest {
    @Test
    public void testPut() {
        LimitedSizeBufferedData<MockStorageData> collection = new LimitedSizeBufferedData<>(5);
        collection.accept(new MockStorageData(1));
        collection.accept(new MockStorageData(3));
        collection.accept(new MockStorageData(5));
        collection.accept(new MockStorageData(7));
        collection.accept(new MockStorageData(9));

        MockStorageData income = new MockStorageData(4);
        collection.accept(income);

        int[] expected = new int[] {
            3,
            4,
            5,
            7,
            9
        };
        int i = 0;
        for (MockStorageData data : collection.read()) {
            Assert.assertEquals(expected[i++], data.latency);
        }
    }

    private class MockStorageData implements ComparableStorageData {
        private long latency;

        public MockStorageData(long latency) {
            this.latency = latency;
        }

        @Override
        public int compareTo(Object o) {
            MockStorageData target = (MockStorageData) o;
            return (int) (latency - target.latency);
        }

        @Override
        public String id() {
            return "id";
        }

        @Override
        public boolean equals(Object o) {
            return true;
        }

        @Override
        public int hashCode() {
            return Objects.hash(1);
        }
    }
}
