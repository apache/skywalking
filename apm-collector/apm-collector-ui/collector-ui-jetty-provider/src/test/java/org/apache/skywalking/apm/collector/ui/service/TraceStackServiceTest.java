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

package org.apache.skywalking.apm.collector.ui.service;

import com.google.common.collect.Lists;
import org.apache.skywalking.apm.collector.cache.service.ApplicationCacheService;
import org.apache.skywalking.apm.collector.cache.service.NetworkAddressCacheService;
import org.apache.skywalking.apm.collector.cache.service.ServiceNameCacheService;
import org.apache.skywalking.apm.collector.core.module.MockModule;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.storage.dao.ui.IGlobalTraceUIDAO;
import org.apache.skywalking.apm.collector.storage.dao.ui.ISegmentUIDAO;
import org.apache.skywalking.apm.collector.storage.table.register.Application;
import org.apache.skywalking.apm.collector.storage.ui.trace.Trace;
import org.apache.skywalking.apm.network.proto.*;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.internal.util.reflection.Whitebox;

import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

/**
 * @author lican
 */
public class TraceStackServiceTest {

    private TraceStackService traceStackService;
    private IGlobalTraceUIDAO globalTraceDAO;
    private ISegmentUIDAO segmentDAO;
    private ApplicationCacheService applicationCacheService;
    private ServiceNameCacheService serviceNameCacheService;
    private NetworkAddressCacheService networkAddressCacheService;

    @Before
    public void setUp() throws Exception {
        ModuleManager moduleManager = mock(ModuleManager.class);
        when(moduleManager.find(anyString())).then(invocation -> new MockModule());
        traceStackService = new TraceStackService(moduleManager);
        globalTraceDAO = mock(IGlobalTraceUIDAO.class);
        segmentDAO = mock(ISegmentUIDAO.class);
        applicationCacheService = mock(ApplicationCacheService.class);
        Whitebox.setInternalState(traceStackService, "globalTraceDAO", globalTraceDAO);
        Whitebox.setInternalState(traceStackService, "segmentDAO", segmentDAO);
        Whitebox.setInternalState(traceStackService, "applicationCacheService", applicationCacheService);
    }

    @Test
    public void load() {
        when(globalTraceDAO.getSegmentIds(anyString())).then(invocation -> Lists.newArrayList("1", "2", "3"));
        when(segmentDAO.load(anyString())).then(invocation -> {
            TraceSegmentReference traceSegmentReference = TraceSegmentReference.newBuilder()
                    .setRefType(RefType.CrossProcess)
                    .setRefTypeValue(1)
                    .build();
            LogMessage message = LogMessage.newBuilder()
                    .setTime(System.currentTimeMillis())
                    .addData(KeyWithStringValue.newBuilder().setKey("a").setValue("b").build())
                    .build();
            SpanObject testSpanObject = SpanObject.newBuilder()
                    .setSpanId(1)
                    .setOperationName("testSpanName")
                    .addLogs(message)
                    .addTags(KeyWithStringValue.newBuilder().setKey("tagKey").setValue("tagValue").build())
                    .setOperationNameId(1)
                    .addRefs(traceSegmentReference)
                    .build();
            return TraceSegmentObject.newBuilder()
                    .addSpans(testSpanObject)
                    .build();
        });
        mockCache();
        Trace load = traceStackService.load("123");
        Assert.assertNotNull(load);
    }

    private void mockCache() {
        Mockito.when(applicationCacheService.getApplicationById(anyInt())).then(invocation -> {
            Application application = new Application();
            application.setApplicationId(1);
            application.setApplicationCode("test");
            return application;
        });
    }
}