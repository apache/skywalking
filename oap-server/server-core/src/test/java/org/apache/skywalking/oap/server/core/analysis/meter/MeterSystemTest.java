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

package org.apache.skywalking.oap.server.core.analysis.meter;

import java.util.Map;
import org.apache.skywalking.oap.server.core.analysis.StreamDefinition;
import org.apache.skywalking.oap.server.core.analysis.worker.MetricsStreamProcessor;
import org.apache.skywalking.oap.server.core.storage.StorageException;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.powermock.reflect.Whitebox;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.spy;

@RunWith(MockitoJUnitRunner.Silent.class)
public class MeterSystemTest {

    @Mock
    private ModuleManager moduleManager;
    private MeterSystem meterSystem;

    @Before
    public void setup() throws StorageException {
        meterSystem = spy(new MeterSystem(moduleManager));
        Whitebox.setInternalState(MetricsStreamProcessor.class, "PROCESSOR",
                                  Mockito.spy(MetricsStreamProcessor.getInstance()));
        doNothing().when(MetricsStreamProcessor.getInstance()).create(any(), (StreamDefinition) any(), any());
    }

    @Test
    public void testCreate() {
        // validate with same name, function and scope types
        meterSystem.create("test_meter", "avg", ScopeType.SERVICE);
        validateMeterDefinition("test_meter", Long.class, ScopeType.SERVICE);
        meterSystem.create("test_meter", "avg", ScopeType.SERVICE);
        validateMeterDefinition("test_meter", Long.class, ScopeType.SERVICE);

        // validate with same name, difference scope type
        try {
            meterSystem.create("test_meter", "avg", ScopeType.SERVICE_INSTANCE);
            throw new IllegalStateException();
        } catch (IllegalArgumentException e) {
            // If wrong arguments is means right
        }

        // validate with same name, difference function
        try {
            meterSystem.create("test_meter", "avgLabeled", ScopeType.SERVICE);
            throw new IllegalStateException();
        } catch (IllegalArgumentException e) {
            // If wrong arguments is means right
        }
    }

    private void validateMeterDefinition(String meterName, Class<?> dataType, ScopeType type) {
        Map<String, ?> meterPrototypes = Whitebox.getInternalState(meterSystem, "meterPrototypes");
        Object meterDefinition = meterPrototypes.get(meterName);
        Class<?> realDataType = Whitebox.getInternalState(meterDefinition, "dataType");
        ScopeType realScopeTypes = Whitebox.getInternalState(meterDefinition, "scopeType");

        Assert.assertEquals(dataType, realDataType);
        Assert.assertEquals(type, realScopeTypes);
    }

}
