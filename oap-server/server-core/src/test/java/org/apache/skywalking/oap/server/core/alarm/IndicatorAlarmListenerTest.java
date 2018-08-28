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

package org.apache.skywalking.oap.server.core.alarm;

import java.util.Map;
import org.apache.skywalking.oap.server.core.analysis.indicator.LongValueHolder;
import org.apache.skywalking.oap.server.core.analysis.indicator.annotation.IndicatorType;
import org.apache.skywalking.oap.server.core.storage.StorageBuilder;
import org.apache.skywalking.oap.server.core.storage.StorageData;
import org.apache.skywalking.oap.server.core.storage.annotation.StorageEntity;
import org.junit.Assert;
import org.junit.Test;

public class IndicatorAlarmListenerTest {
    @Test
    public void testIndicatorPreAnalysis() {
        IndicatorAlarmListener listener = new IndicatorAlarmListener();
        listener.notify(ATestClass.class);
        listener.notify(UnCompleteIndicator.class);
        listener.notify(MockIndicator.class);

        Assert.assertNull(listener.getTarget(ATestClass.class));
        Assert.assertNull(listener.getTarget(UnCompleteIndicator.class));
        Assert.assertNotNull(listener.getTarget(MockIndicator.class));
    }

    public class ATestClass {

    }

    @IndicatorType
    public class UnCompleteIndicator {

    }

    @IndicatorType
    @StorageEntity(name = "mock_indicator", builder = MockBuilder.class)
    public class MockIndicator implements LongValueHolder {

        @Override public long getValue() {
            return 0;
        }
    }

    public class MockBuilder implements StorageBuilder {

        @Override public StorageData map2Data(Map dbMap) {
            return null;
        }

        @Override public Map<String, Object> data2Map(StorageData storageData) {
            return null;
        }
    }
}
