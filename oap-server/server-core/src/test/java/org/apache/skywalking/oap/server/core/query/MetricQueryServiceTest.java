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

import com.google.common.collect.Lists;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.query.entity.IntValues;
import org.apache.skywalking.oap.server.core.query.entity.Step;
import org.apache.skywalking.oap.server.core.query.entity.Thermodynamic;
import org.apache.skywalking.oap.server.core.query.sql.Function;
import org.apache.skywalking.oap.server.core.query.sql.KeyValues;
import org.apache.skywalking.oap.server.core.query.sql.Where;
import org.apache.skywalking.oap.server.core.storage.annotation.ValueColumnIds;
import org.apache.skywalking.oap.server.core.storage.query.IMetricsQueryDAO;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.powermock.reflect.Whitebox;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

/**
 * Created by dengming in 2019-05-18
 */
public class MetricQueryServiceTest extends AbstractTest {

    private MetricQueryService queryService = new MetricQueryService(moduleManager);

    private IMetricsQueryDAO queryDAO = mock(IMetricsQueryDAO.class);

    private static final String IND_NAME = "ind-name";
    private static final String VALUE_C_NAME = "value-c-name";
    private static final Function FUNCTION = Function.None;

    @Before
    public void setUp() throws Exception {

        ValueColumnIds.INSTANCE.putIfAbsent(IND_NAME, VALUE_C_NAME, FUNCTION);

        when(moduleServiceHolder.getService(IMetricsQueryDAO.class)).thenReturn(queryDAO);
    }

    @After
    public void tearDown() throws Exception {
        //clear the instance
        Whitebox.setInternalState(ValueColumnIds.INSTANCE, "mapping", new HashMap<>());
    }

    @Test
    public void getValues() throws Exception {
        List<String> ids = Lists.newArrayList("1", "2", "3", "4");

        ArgumentCaptor<Where> whereArgumentCaptor = ArgumentCaptor.forClass(Where.class);
        ArgumentCaptor<String> cNameArgumentCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Function> functionArgumentCaptor = ArgumentCaptor.forClass(Function.class);

        IntValues mockValues = mock(IntValues.class);

        when(queryDAO.getValues(anyString(), any(Step.class), anyLong(), anyLong(),
                whereArgumentCaptor.capture(), cNameArgumentCaptor.capture(), functionArgumentCaptor.capture()))
                .thenReturn(mockValues);

        IntValues intValues = queryService.getValues(IND_NAME, ids, Step.SECOND, START_TB, END_TB);

        Where where = whereArgumentCaptor.getValue();
        String cName = cNameArgumentCaptor.getValue();
        Function function = functionArgumentCaptor.getValue();

        assertEquals(VALUE_C_NAME, cName);
        assertEquals(function, FUNCTION);
        assertEquals(mockValues, intValues);

        assertEquals(1, where.getKeyValues().size());
        KeyValues keyValues = where.getKeyValues().get(0);
        assertEquals(Metrics.ENTITY_ID, keyValues.getKey());
        assertEquals(ids, keyValues.getValues());

    }

    @Test(expected = IllegalArgumentException.class)
    public void getValuesWithEmptyId() throws Exception {
        queryService.getValues(IND_NAME, Collections.emptyList(), Step.SECOND, START_TB, END_TB);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void getLinearIntValues() throws Exception {

        ArgumentCaptor<List> idsCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<String> cNameCaptor = ArgumentCaptor.forClass(String.class);

        IntValues mockIntValues = mock(IntValues.class);
        when(queryDAO.getLinearIntValues(anyString(), any(Step.class), idsCaptor.capture(), cNameCaptor.capture()))
                .thenReturn(mockIntValues);
        String id = "id";
        IntValues intValues = queryService.getLinearIntValues(IND_NAME, id, Step.MONTH, START_TB, END_TB);

        List<String> idList = idsCaptor.getValue();
        String cName = cNameCaptor.getValue();

        assertEquals(VALUE_C_NAME, cName);
        assertEquals(END_TB - START_TB + 1, idList.size());

        for (int i = 0; i < idList.size(); i++) {
            assertEquals((START_TB + i) + Const.ID_SPLIT + id, idList.get(i));
        }
        assertEquals(mockIntValues, intValues);


        // empty id
        intValues = queryService.getLinearIntValues(IND_NAME, null, Step.MONTH, START_TB, END_TB);

        idList = idsCaptor.getValue();
        cName = cNameCaptor.getValue();

        assertEquals(VALUE_C_NAME, cName);
        assertEquals(END_TB - START_TB + 1, idList.size());

        for (int i = 0; i < idList.size(); i++) {
            assertEquals(String.valueOf(START_TB + i), idList.get(i));
        }
        assertEquals(mockIntValues, intValues);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void getThermodynamic() throws Exception {

        Thermodynamic mockThermodynamic = mock(Thermodynamic.class);

        ArgumentCaptor<String> indNameCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<List> idsCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<String> cNameCaptor = ArgumentCaptor.forClass(String.class);

        when(queryDAO.getThermodynamic(indNameCaptor.capture(), any(Step.class),
                idsCaptor.capture(), cNameCaptor.capture())).thenReturn(mockThermodynamic);

        String id = "id";
        Thermodynamic thermodynamic = queryService.getThermodynamic(IND_NAME, id, Step.MONTH, START_TB, END_TB);
        assertEquals(mockThermodynamic, thermodynamic);

        List<String> ids = idsCaptor.getValue();

        assertEquals(IND_NAME, indNameCaptor.getValue());
        assertEquals(END_TB - START_TB + 1, ids.size());
        assertEquals(VALUE_C_NAME, cNameCaptor.getValue());


        for (int i = 0; i < ids.size(); i++) {
            assertEquals((START_TB + i) + Const.ID_SPLIT + id, ids.get(i));
        }


        thermodynamic = queryService.getThermodynamic(IND_NAME, null, Step.MONTH, START_TB, END_TB);

        assertEquals(mockThermodynamic, thermodynamic);

        ids = idsCaptor.getValue();

        assertEquals(IND_NAME, indNameCaptor.getValue());
        assertEquals(END_TB - START_TB + 1, ids.size());
        assertEquals(VALUE_C_NAME, cNameCaptor.getValue());
        for (int i = 0; i < ids.size(); i++) {
            assertEquals(String.valueOf(START_TB + i), ids.get(i));
        }

    }
}