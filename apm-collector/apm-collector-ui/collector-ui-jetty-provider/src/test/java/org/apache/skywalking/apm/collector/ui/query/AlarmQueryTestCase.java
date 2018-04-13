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

package org.apache.skywalking.apm.collector.ui.query;

import org.apache.skywalking.apm.collector.storage.ui.alarm.AlarmType;
import org.apache.skywalking.apm.collector.storage.ui.common.Duration;
import org.apache.skywalking.apm.collector.storage.ui.common.Pagination;
import org.apache.skywalking.apm.collector.storage.ui.common.Step;
import org.apache.skywalking.apm.collector.ui.service.AlarmService;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.internal.util.reflection.Whitebox;

import java.text.ParseException;

/**
 * @author lican
 */
public class AlarmQueryTestCase {

    @Test
    public void testLoadAlarmList() throws ParseException {
        AlarmQuery query = new AlarmQuery(null);
        AlarmService alarmService = Mockito.mock(AlarmService.class);
        Whitebox.setInternalState(query, "alarmService", alarmService);
        Mockito.when(alarmService.loadApplicationAlarmList(
                Mockito.anyString(), Mockito.anyObject(),
                Mockito.anyLong(), Mockito.anyLong(), Mockito.anyInt(), Mockito.anyInt()
        )).then(invocation -> {
            Object[] arguments = invocation.getArguments();
            Assert.assertEquals(201701000000L, arguments[2]);
            Assert.assertEquals(201701999999L, arguments[3]);
            return null;
        });

        Mockito.when(alarmService.loadInstanceAlarmList(
                Mockito.anyString(), Mockito.anyObject(),
                Mockito.anyLong(), Mockito.anyLong(), Mockito.anyInt(), Mockito.anyInt()
        )).then(invocation -> {
            Object[] arguments = invocation.getArguments();
            Assert.assertEquals(201701000000L, arguments[2]);
            Assert.assertEquals(201701999999L, arguments[3]);
            return null;
        });

        Mockito.when(alarmService.loadServiceAlarmList(
                Mockito.anyString(), Mockito.anyObject(),
                Mockito.anyLong(), Mockito.anyLong(), Mockito.anyInt(), Mockito.anyInt()
        )).then(invocation -> {
            Object[] arguments = invocation.getArguments();
            Assert.assertEquals(201701000000L, arguments[2]);
            Assert.assertEquals(201701999999L, arguments[3]);
            return null;
        });

        Duration duration = new Duration();
        duration.setStart("2017-01");
        duration.setEnd("2017-01");
        duration.setStep(Step.MONTH);
        Pagination pagination = new Pagination();
        query.loadAlarmList("keyword", AlarmType.APPLICATION, duration, pagination);
        query.loadAlarmList("keyword", AlarmType.SERVER, duration, pagination);
        query.loadAlarmList("keyword", AlarmType.SERVICE, duration, pagination);
    }

}
