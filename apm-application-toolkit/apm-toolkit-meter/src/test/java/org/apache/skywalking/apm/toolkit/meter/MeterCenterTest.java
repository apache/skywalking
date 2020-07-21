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

package org.apache.skywalking.apm.toolkit.meter;

import org.apache.skywalking.apm.toolkit.meter.impl.AbstractMeter;
import org.apache.skywalking.apm.toolkit.meter.impl.MeterCenter;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.powermock.reflect.Whitebox;

import java.util.Collections;
import java.util.Map;

public class MeterCenterTest {

    private Map<MeterId, AbstractMeter> meterMap;

    @Before
    public void setup() {
        meterMap = Whitebox.getInternalState(MeterCenter.class, "METER_MAP");
        meterMap.clear();
    }

    @Test
    public void testCreateOrGet() {
        // null
        Assert.assertNull(MeterCenter.getOrCreateMeter(null));

        // simple counter
        final Counter counter = MeterFactory.counter("test").build();
        final MeterId counterMeterId = Whitebox.getInternalState(counter, "meterId");
        Assert.assertNotNull(counterMeterId);
        Assert.assertNotNull(meterMap.get(counterMeterId));
        Assert.assertEquals(meterMap.get(counterMeterId), counter);

        // same counter
        Assert.assertEquals(counter, MeterFactory.counter("test").build());
        Assert.assertEquals(meterMap.size(), 1);
        Assert.assertNotNull(meterMap.get(counterMeterId));
        Assert.assertEquals(meterMap.get(counterMeterId), counter);
    }

    @Test
    public void testRemoveMeter() {
        final Counter counter = MeterFactory.counter("test").build();
        Assert.assertEquals(meterMap.size(), 1);
        final MeterId counterMeterId = Whitebox.getInternalState(counter, "meterId");
        MeterCenter.removeMeter(counterMeterId);
        Assert.assertEquals(meterMap.size(), 0);

        // not registered meter id
        final MeterId newMeterId = new MeterId("test1", MeterId.MeterType.COUNTER, Collections.emptyList());
        MeterCenter.removeMeter(newMeterId);
        Assert.assertEquals(meterMap.size(), 0);

        // remove null
        MeterCenter.removeMeter(null);
    }
}
