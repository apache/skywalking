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

import org.apache.skywalking.oap.server.core.query.entity.Order;
import org.apache.skywalking.oap.server.core.query.entity.TopNRecord;
import org.apache.skywalking.oap.server.core.storage.query.ITopNRecordsQueryDAO;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Created by dengming in 2019-05-18
 */
public class TopNRecordsQueryServiceTest extends AbstractTest {

    private TopNRecordsQueryService queryService = new TopNRecordsQueryService(moduleManager);
    private ITopNRecordsQueryDAO queryDAO = mock(ITopNRecordsQueryDAO.class);

    @Before
    public void setUp() throws Exception {
        when(moduleServiceHolder.getService(ITopNRecordsQueryDAO.class)).thenReturn(queryDAO);
    }

    @Test
    public void getTopNRecords() throws Exception {
        long startSecondTB=20190516013223L;
        long endSecondTB = 20190516014223L;
        String metricName = "metricName";
        int serviceId = 1;
        int topN = 10;

        TopNRecord record = new TopNRecord();

        List<TopNRecord> mockRecordList = new ArrayList<>(topN);
        for (int i = 0; i < topN; i++) {
            mockRecordList.add(record);
        }

        when(queryDAO.getTopNRecords(anyLong(), anyLong(), anyString(), anyInt(), anyInt(), any(Order.class)))
                .thenReturn(mockRecordList);

        List<TopNRecord> recordList = queryService.getTopNRecords(startSecondTB, endSecondTB, metricName,
                serviceId, topN, Order.ASC);
        assertEquals(10, recordList.size());
        assertEquals(mockRecordList, recordList);
    }
}