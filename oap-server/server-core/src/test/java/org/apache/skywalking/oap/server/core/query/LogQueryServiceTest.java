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

import org.apache.skywalking.oap.server.core.query.entity.Log;
import org.apache.skywalking.oap.server.core.query.entity.LogState;
import org.apache.skywalking.oap.server.core.query.entity.Logs;
import org.apache.skywalking.oap.server.core.query.entity.Pagination;
import org.apache.skywalking.oap.server.core.storage.query.ILogQueryDAO;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.*;

/**
 * Created by dengming in 2019-05-17
 */
public class LogQueryServiceTest extends AbstractTest {

    private LogQueryService logQueryService = new LogQueryService(moduleManager);

    private ILogQueryDAO logQueryDAO = mock(ILogQueryDAO.class);

    private static final int TOTAL = 10;

    @Before
    public void setUp() throws Exception {
        when(moduleServiceHolder.getService(ILogQueryDAO.class)).thenReturn(logQueryDAO);

        Logs logs = mock(Logs.class);

        List<Log> logList = new ArrayList<>(TOTAL);
        for (int i = 1; i <= TOTAL; i++) {
            Log log = new Log();
            log.setServiceId(i);
            log.setServiceInstanceId(i * 10);
            log.setEndpointId(i * 100);
            logList.add(log);
        }

        when(logs.getTotal()).thenReturn(TOTAL);
        when(logs.getLogs()).thenReturn(logList);

        when(logQueryDAO.queryLogs(anyString(), anyInt(), anyInt(), anyInt(), anyString(),
                any(LogState.class), anyString(), any(Pagination.class), anyInt(),
                anyInt(), anyLong(), anyLong())).thenReturn(logs);

//        when(serviceInventoryCache.get(anyInt())).thenReturn()
    }

    @Test
    public void queryLogs() throws Exception {
        Logs logs = logQueryService.queryLogs("metric-name", 123,
                1234, 12345, "trace-id",

                LogState.SUCCESS, "state", PAGINATION, 1234L, 2345L);
        assertNotNull(logs);
        assertEquals(TOTAL, logs.getTotal());
        List<Log> logList = logs.getLogs();
        assertEquals(TOTAL, logList.size());

        for (int i = 0; i < TOTAL; i++) {
            Log log = logList.get(i);

            assertEquals(SERVICE_INSTANCE_INVENTORY_NAME, log.getServiceInstanceName());
            assertEquals(SERVICE_INVENTORY_NAME, log.getServiceName());
            assertEquals(ENDPOINT_INVENTORY_NAME, log.getEndpointName());
        }

    }
}