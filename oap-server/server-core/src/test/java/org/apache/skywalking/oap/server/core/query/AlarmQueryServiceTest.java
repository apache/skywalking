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
package org.apache.skywalking.oap.server.core.query;

import org.apache.skywalking.oap.server.core.query.entity.Alarms;
import org.apache.skywalking.oap.server.core.query.entity.Pagination;
import org.apache.skywalking.oap.server.core.storage.query.IAlarmQueryDAO;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.apache.skywalking.oap.server.library.module.ModuleProviderHolder;
import org.apache.skywalking.oap.server.library.module.ModuleServiceHolder;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Created by dengming in 2019-05-15
 */
public class AlarmQueryServiceTest extends AbstractTest {

    private IAlarmQueryDAO alarmQueryDAO = mock(IAlarmQueryDAO.class);

    private AlarmQueryService alarmQueryService = new AlarmQueryService(moduleManager);

    private static final int SCOPE_ID = 1;

    private static final String KEY_WORD = "key-word";

    private static final Pagination PAGINATION = new Pagination();

    private static final int PAGE_SIZE = 100;
    private static final int PAGE_NUM = 10;

    private static final long START_TB = 123L;

    private static final long END_TB = 13L;

    private static final int ALARM_TOTAL = 100;



    @Before
    public void setUp() throws Exception {

        when(moduleServiceHolder.getService(IAlarmQueryDAO.class)).thenReturn(alarmQueryDAO);

        PAGINATION.setPageSize(PAGE_SIZE);
        PAGINATION.setPageNum(PAGE_NUM);
        Alarms alarms = new Alarms();
        alarms.setTotal(ALARM_TOTAL);

        when(alarmQueryDAO.getAlarm(anyInt(), anyString(), anyInt(), anyInt(), anyLong(), anyLong()))
                .thenReturn(alarms);
    }

    @Test
    public void getAlarm() throws Exception {
        Alarms alarms = alarmQueryService.getAlarm(SCOPE_ID, KEY_WORD, PAGINATION, START_TB, END_TB);
        assertEquals(ALARM_TOTAL, alarms.getTotal());
    }


}