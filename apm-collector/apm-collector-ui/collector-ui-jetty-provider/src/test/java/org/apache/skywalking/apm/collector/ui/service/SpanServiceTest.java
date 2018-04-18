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

import com.google.gson.JsonObject;
import org.apache.skywalking.apm.collector.cache.service.ApplicationCacheService;
import org.apache.skywalking.apm.collector.cache.service.ServiceNameCacheService;
import org.apache.skywalking.apm.collector.core.module.MockModule;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.storage.dao.ui.ISegmentUIDAO;
import org.apache.skywalking.apm.network.proto.KeyWithStringValue;
import org.apache.skywalking.apm.network.proto.LogMessage;
import org.apache.skywalking.apm.network.proto.SpanObject;
import org.apache.skywalking.apm.network.proto.TraceSegmentObject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.internal.util.reflection.Whitebox;

import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

/**
 * @author lican
 */
public class SpanServiceTest {

    private SpanService spanService;
    private ISegmentUIDAO segmentDAO;
    private ServiceNameCacheService serviceNameCacheService;
    private ApplicationCacheService applicationCacheService;

    @Before
    public void setUp() throws Exception {
        ModuleManager moduleManager = mock(ModuleManager.class);
        when(moduleManager.find(anyString())).then(invocation -> new MockModule());
        spanService = new SpanService(moduleManager);
        serviceNameCacheService = mock(ServiceNameCacheService.class);
        applicationCacheService = mock(ApplicationCacheService.class);
        segmentDAO = mock(ISegmentUIDAO.class);
        Whitebox.setInternalState(spanService, "serviceNameCacheService", serviceNameCacheService);
        Whitebox.setInternalState(spanService, "applicationCacheService", applicationCacheService);
        Whitebox.setInternalState(spanService, "segmentDAO", segmentDAO);
    }

    @Test
    public void load() {
        when(segmentDAO.load(anyString())).then(invocation -> {
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
                    .build();
            return TraceSegmentObject.newBuilder()
                    .addSpans(testSpanObject)
                    .build();
        });

        JsonObject load = spanService.load("123", 1);
        Assert.assertNotNull(load);
    }
}