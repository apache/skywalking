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

import org.apache.skywalking.oap.server.core.analysis.StreamDefinition;
import org.apache.skywalking.oap.server.core.analysis.worker.MetricsStreamProcessor;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.spy;

@ExtendWith(MockitoExtension.class)
public class MeterSystemTest {
    @Mock
    private ModuleManager moduleManager;
    private MeterSystem meterSystem;
    private MockedStatic<MetricsStreamProcessor> mockedProcessor;
    private MetricsStreamProcessor processorMock;

    @BeforeEach
    public void setup() throws Exception {
        meterSystem = spy(new MeterSystem(moduleManager));
        processorMock = Mockito.mock(MetricsStreamProcessor.class);
        mockedProcessor = Mockito.mockStatic(MetricsStreamProcessor.class);
        mockedProcessor.when(MetricsStreamProcessor::getInstance).thenReturn(processorMock);
        doNothing().when(processorMock).create(any(), (StreamDefinition) any(), any());
    }

    @AfterEach
    public void tearDown() {
        if (mockedProcessor != null) {
            mockedProcessor.close();
        }
    }

    @Test
    public void testCreate() throws Exception {
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

    private void validateMeterDefinition(String meterName, Class<?> dataType, ScopeType type) throws Exception {
        Field meterPrototypesField = MeterSystem.class.getDeclaredField("meterPrototypes");
        meterPrototypesField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, ?> meterPrototypes = (Map<String, ?>) meterPrototypesField.get(meterSystem);
        Object meterDefinition = meterPrototypes.get(meterName);
        Field dataTypeField = meterDefinition.getClass().getDeclaredField("dataType");
        dataTypeField.setAccessible(true);
        Class<?> realDataType = (Class<?>) dataTypeField.get(meterDefinition);
        Field scopeTypeField = meterDefinition.getClass().getDeclaredField("scopeType");
        scopeTypeField.setAccessible(true);
        ScopeType realScopeTypes = (ScopeType) scopeTypeField.get(meterDefinition);

        Assertions.assertEquals(dataType, realDataType);
        Assertions.assertEquals(type, realScopeTypes);
    }

}
