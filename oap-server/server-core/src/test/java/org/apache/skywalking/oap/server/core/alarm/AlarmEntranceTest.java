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
 */

package org.apache.skywalking.oap.server.core.alarm;

import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.library.module.ModuleDefineHolder;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.internal.util.reflection.Whitebox;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author jsbxyyx
 */
public class AlarmEntranceTest {


    @Test
    public void forwardVerifyDoNotInitMethod() throws Exception {

        ModuleDefineHolder moduleDefineHolder = mock(ModuleDefineHolder.class);
        doReturn(false).when(moduleDefineHolder).has(AlarmModule.NAME);

        AlarmEntrance alarmEntrance = new AlarmEntrance(moduleDefineHolder);
        alarmEntrance.forward(mock(Metrics.class));

        Object o = Whitebox.getInternalState(alarmEntrance, "metricsNotify");
        Assert.assertNull(o);
    }


    @Test
    public void forwardVerifyDoInitMethod() throws Exception {

        ModuleDefineHolder moduleDefineHolder = mock(ModuleDefineHolder.class, RETURNS_DEEP_STUBS);
        when(moduleDefineHolder.has(AlarmModule.NAME)).thenReturn(true);

        MetricsNotify metricsNotify = mock(MetricsNotify.class);
        when(moduleDefineHolder.find(AlarmModule.NAME).provider().getService(MetricsNotify.class)).thenReturn(metricsNotify);

        AlarmEntrance alarmEntrance = new AlarmEntrance(moduleDefineHolder);
        alarmEntrance.forward(mock(Metrics.class));
        verify(metricsNotify).notify(any());

        Object o = Whitebox.getInternalState(alarmEntrance, "metricsNotify");
        Assert.assertNotNull(o);
    }

}
