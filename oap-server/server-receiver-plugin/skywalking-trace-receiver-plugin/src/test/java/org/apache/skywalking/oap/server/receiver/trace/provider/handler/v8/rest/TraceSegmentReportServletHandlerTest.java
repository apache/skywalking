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

package org.apache.skywalking.oap.server.receiver.trace.provider.handler.v8.rest;

import org.apache.skywalking.apm.network.language.agent.v3.SegmentObject;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;
import org.apache.skywalking.oap.server.telemetry.api.MetricsCreator;
import org.apache.skywalking.oap.server.telemetry.none.MetricsCreatorNoop;
import org.apache.skywalking.oap.server.telemetry.none.NoneTelemetryProvider;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.internal.util.reflection.Whitebox;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.modules.junit4.PowerMockRunner;

import javax.servlet.http.HttpServletRequest;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.List;

import static org.mockito.Mockito.when;

@RunWith(PowerMockRunner.class)
@PowerMockIgnore({"javax.management.*"})
public class TraceSegmentReportServletHandlerTest {

    @Mock
    private HttpServletRequest request;
    @Mock
    private ModuleManager moduleManager;
    @Mock
    private NoneTelemetryProvider telemetryProvider;

    @Before
    public void init() throws IOException {
        TelemetryModule telemetryModule = Mockito.spy(TelemetryModule.class);
        Whitebox.setInternalState(telemetryModule, "loadedProvider", telemetryProvider);
        Mockito.when(moduleManager.find(TelemetryModule.NAME)).thenReturn(telemetryModule);

        Mockito.when(telemetryProvider.getService(MetricsCreator.class))
            .thenReturn(new MetricsCreatorNoop());
    }

    @Test
    public void testSingle() throws IOException {
        String singleJson = "{" +
            "   \"traceId\":\"c480c738-b628-490d-ace7-69f7030d77cb\"," +
            "   \"spans\":[" +
            "       {\"operationName\":\"\\/ingress\"}" +
            "   ]" +
            "}";

        final TraceSegmentReportSingleServletHandler singleServletHandler =
            new TraceSegmentReportSingleServletHandler(moduleManager, null, null);

        when(request.getReader()).thenReturn(new BufferedReader(new StringReader(singleJson)));
        final List<SegmentObject> segmentObjects = singleServletHandler.parseSegments(request);
        Assert.assertEquals(segmentObjects.size(), 1);
    }

    @Test
    public void testListJson() throws IOException {
        String listJson = "[{" +
            "   \"traceId\":\"c480c738-b628-490d-ace7-69f7030d77cb\"," +
            "   \"spans\":[" +
            "       {\"operationName\":\"\\/ingress\"}" +
            "   ]" +
            "},{" +
            "   \"traceId\":\"e9673310-cf3a-467e-8f47-eaec26b57f76\"," +
            "   \"spans\":[" +
            "       {\"operationName\":\"\\/ingress\"}" +
            "   ]" +
            "}]";

        final TraceSegmentReportListServletHandler singleServletHandler =
            new TraceSegmentReportListServletHandler(moduleManager, null, null);

        when(request.getReader()).thenReturn(new BufferedReader(new StringReader(listJson)));
        final List<SegmentObject> segmentObjects = singleServletHandler.parseSegments(request);
        Assert.assertEquals(segmentObjects.size(), 2);
    }

}
