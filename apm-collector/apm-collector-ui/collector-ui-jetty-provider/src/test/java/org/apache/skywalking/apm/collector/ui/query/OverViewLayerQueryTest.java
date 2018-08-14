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
import org.apache.skywalking.apm.collector.storage.ui.overview.ClusterBrief;
import org.apache.skywalking.apm.collector.ui.service.*;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.internal.util.reflection.Whitebox;

import java.text.ParseException;
import java.util.Collections;

/**
 * @author lican
 */
public class OverViewLayerQueryTest {


    private ClusterTopologyService clusterTopologyService;
    private ApplicationService applicationService;
    private NetworkAddressService networkAddressService;
    private ServiceNameService serviceNameService;
    private AlarmService alarmService;
    private OverViewLayerQuery overViewLayerQuery;

    @Before
    public void setUp() throws Exception {
        overViewLayerQuery = new OverViewLayerQuery(null);
        alarmService = Mockito.mock(AlarmService.class);
        serviceNameService = Mockito.mock(ServiceNameService.class);
        networkAddressService = Mockito.mock(NetworkAddressService.class);
        applicationService = Mockito.mock(ApplicationService.class);
        clusterTopologyService = Mockito.mock(ClusterTopologyService.class);
        Whitebox.setInternalState(overViewLayerQuery, "alarmService", alarmService);
        Whitebox.setInternalState(overViewLayerQuery, "serviceNameService", serviceNameService);
        Whitebox.setInternalState(overViewLayerQuery, "networkAddressService", networkAddressService);
        Whitebox.setInternalState(overViewLayerQuery, "applicationService", applicationService);
        Whitebox.setInternalState(overViewLayerQuery, "clusterTopologyService", clusterTopologyService);
    }

    @Test
    public void getClusterTopology() throws ParseException {
        Mockito.when(
                clusterTopologyService.getClusterTopology(
                        Mockito.anyObject(),
                        Mockito.anyLong(), Mockito.anyLong(), Mockito.anyLong(),
                        Mockito.anyLong())
        ).then(invocation -> {
            Object[] arguments = invocation.getArguments();
            Assert.assertEquals(201701L, arguments[1]);
            Assert.assertEquals(201702L, arguments[2]);
            Assert.assertEquals(20170100000000L, arguments[3]);
            Assert.assertEquals(20170299999999L, arguments[4]);
            return null;
        });

        Duration duration = new Duration();
        duration.setStart("2017-01");
        duration.setEnd("2017-02");
        duration.setStep(Step.MONTH);

        overViewLayerQuery.getClusterTopology(duration);
    }

    @Test
    public void getClusterBrief() throws ParseException {

        Mockito.when(applicationService.getApplications(Mockito.anyLong(), Mockito.anyLong())).then(invocation -> {
            Object[] arguments = invocation.getArguments();
            Assert.assertEquals(20170100000000L, arguments[0]);
            Assert.assertEquals(20170299999999L, arguments[1]);
            return Collections.emptyList();
        });

        Duration duration = new Duration();
        duration.setStart("2017-01");
        duration.setEnd("2017-02");
        duration.setStep(Step.MONTH);
        ClusterBrief clusterBrief = overViewLayerQuery.getClusterBrief(duration);
        Assert.assertNotNull(clusterBrief);

    }

    @Test
    public void getAlarmTrend() throws ParseException {
        Mockito.when(
                alarmService.getApplicationAlarmTrend(
                        Mockito.anyObject(),
                        Mockito.anyLong(), Mockito.anyLong(),
                        Mockito.anyLong(), Mockito.anyLong())
        ).then(invocation -> {
            Object[] arguments = invocation.getArguments();
            Assert.assertEquals(201701L, arguments[1]);
            Assert.assertEquals(201702L, arguments[2]);
            Assert.assertEquals(20170100000000L, arguments[3]);
            Assert.assertEquals(20170299999999L, arguments[4]);
            return null;
        });
        Duration duration = new Duration();
        duration.setStart("2017-01");
        duration.setEnd("2017-02");
        duration.setStep(Step.MONTH);
        overViewLayerQuery.getAlarmTrend(duration);
    }

    @Test
    public void getConjecturalApps() throws ParseException {
        Mockito.when(
                applicationService.getConjecturalApps(
                        Mockito.anyObject(),
                        Mockito.anyLong(), Mockito.anyLong())
        ).then(invocation -> {
            Object[] arguments = invocation.getArguments();
            Assert.assertEquals(20170100000000L, arguments[1]);
            Assert.assertEquals(20170299999999L, arguments[2]);
            return null;
        });
        Duration duration = new Duration();
        duration.setStart("2017-01");
        duration.setEnd("2017-02");
        duration.setStep(Step.MONTH);
        overViewLayerQuery.getConjecturalApps(duration);
    }

    @Test
    public void getTopNSlowService() throws ParseException {
        Mockito.when(serviceNameService.getSlowService(
                Mockito.anyObject(),
                Mockito.anyLong(), Mockito.anyLong(),
                Mockito.anyLong(), Mockito.anyLong(),
                Mockito.anyInt()
        )).then(invocation -> {
            Object[] arguments = invocation.getArguments();
            Assert.assertEquals(201701L, arguments[1]);
            Assert.assertEquals(201702L, arguments[2]);
            Assert.assertEquals(20170100000000L, arguments[3]);
            Assert.assertEquals(20170299999999L, arguments[4]);
            return null;
        });
        Duration duration = new Duration();
        duration.setStart("2017-01");
        duration.setEnd("2017-02");
        duration.setStep(Step.MONTH);
        overViewLayerQuery.getTopNSlowService(duration, -1);
    }

    @Test
    public void getTopNApplicationThroughput() throws ParseException {
        Mockito.when(applicationService.getTopNApplicationThroughput(
                Mockito.anyObject(),
                Mockito.anyLong(), Mockito.anyLong(),
                Mockito.anyInt()
        )).then(invocation -> {
            Object[] arguments = invocation.getArguments();
            Assert.assertEquals(201701L, arguments[1]);
            Assert.assertEquals(201702L, arguments[2]);
            return null;
        });
        Duration duration = new Duration();
        duration.setStart("2017-01");
        duration.setEnd("2017-02");
        duration.setStep(Step.MONTH);
        overViewLayerQuery.getTopNApplicationThroughput(duration, -1);
    }
}