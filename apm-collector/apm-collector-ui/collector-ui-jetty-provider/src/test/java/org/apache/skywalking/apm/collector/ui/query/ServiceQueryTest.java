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

import org.apache.skywalking.apm.collector.storage.ui.common.Duration;
import org.apache.skywalking.apm.collector.storage.ui.common.Step;
import org.apache.skywalking.apm.collector.ui.service.ServiceNameService;
import org.apache.skywalking.apm.collector.ui.service.ServiceTopologyService;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.internal.util.reflection.Whitebox;

import java.text.ParseException;

/**
 * @author lican
 */
public class ServiceQueryTest {

    private ServiceNameService serviceNameService;
    private ServiceTopologyService serviceTopologyService;
    private ServiceQuery serviceQuery;

    @Before
    public void setUp() throws Exception {
        serviceQuery = new ServiceQuery(null);
        serviceNameService = Mockito.mock(ServiceNameService.class);
        serviceTopologyService = Mockito.mock(ServiceTopologyService.class);
        Whitebox.setInternalState(serviceQuery, "serviceNameService", serviceNameService);
        Whitebox.setInternalState(serviceQuery, "serviceTopologyService", serviceTopologyService);
    }

    @Test
    public void searchService() throws ParseException {
        serviceQuery.searchService("keyword", -1);
    }

    @Test
    public void getServiceResponseTimeTrend() throws ParseException {
        Mockito.when(serviceNameService.getServiceResponseTimeTrend(
                Mockito.anyInt(), Mockito.anyObject(),
                Mockito.anyLong(), Mockito.anyLong())
        ).then(invocation -> {
            Object[] arguments = invocation.getArguments();
            Assert.assertEquals(201701L, arguments[2]);
            Assert.assertEquals(201702L, arguments[3]);
            return null;
        });
        Duration duration = new Duration();
        duration.setStart("2017-01");
        duration.setEnd("2017-02");
        duration.setStep(Step.MONTH);
        serviceQuery.getServiceResponseTimeTrend(-1, duration);
    }

    @Test
    public void getServiceTPSTrend() throws ParseException {
        Mockito.when(serviceNameService.getServiceTPSTrend(
                Mockito.anyInt(), Mockito.anyObject(),
                Mockito.anyLong(), Mockito.anyLong())
        ).then(invocation -> {
            Object[] arguments = invocation.getArguments();
            Assert.assertEquals(201701L, arguments[2]);
            Assert.assertEquals(201702L, arguments[3]);
            return null;
        });
        Duration duration = new Duration();
        duration.setStart("2017-01");
        duration.setEnd("2017-02");
        duration.setStep(Step.MONTH);
        serviceQuery.getServiceTPSTrend(-1, duration);
    }

    @Test
    public void getServiceSLATrend() throws ParseException {
        Mockito.when(serviceNameService.getServiceSLATrend(
                Mockito.anyInt(), Mockito.anyObject(),
                Mockito.anyLong(), Mockito.anyLong())
        ).then(invocation -> {
            Object[] arguments = invocation.getArguments();
            Assert.assertEquals(201701L, arguments[2]);
            Assert.assertEquals(201702L, arguments[3]);
            return null;
        });
        Duration duration = new Duration();
        duration.setStart("2017-01");
        duration.setEnd("2017-02");
        duration.setStep(Step.MONTH);
        serviceQuery.getServiceSLATrend(-1, duration);
    }

    @Test
    public void getServiceTopology() throws ParseException {
        Mockito.when(serviceTopologyService.getServiceTopology(
                Mockito.anyObject(), Mockito.anyInt(),
                Mockito.anyLong(), Mockito.anyLong(),
                Mockito.anyLong(), Mockito.anyLong())
        ).then(invocation -> {
            Object[] arguments = invocation.getArguments();
            Assert.assertEquals(201701L, arguments[2]);
            Assert.assertEquals(201702L, arguments[3]);
            Assert.assertEquals(20170100000000L, arguments[4]);
            Assert.assertEquals(20170299999999L, arguments[5]);
            return null;
        });
        Duration duration = new Duration();
        duration.setStart("2017-01");
        duration.setEnd("2017-02");
        duration.setStep(Step.MONTH);
        serviceQuery.getServiceTopology(-1, duration);
    }
}