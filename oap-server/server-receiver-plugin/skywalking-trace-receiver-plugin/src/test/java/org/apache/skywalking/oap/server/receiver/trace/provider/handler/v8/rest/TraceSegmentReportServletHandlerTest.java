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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import org.apache.skywalking.apm.network.language.agent.v3.SegmentObject;
import org.apache.skywalking.oap.server.analyzer.module.AnalyzerModule;
import org.apache.skywalking.oap.server.analyzer.provider.AnalyzerModuleConfig;
import org.apache.skywalking.oap.server.analyzer.provider.AnalyzerModuleProvider;
import org.apache.skywalking.oap.server.analyzer.provider.trace.parser.ISegmentParserService;
import org.apache.skywalking.oap.server.analyzer.provider.trace.parser.SegmentParserServiceImpl;
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
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.reflect.Whitebox;

import static org.mockito.Mockito.when;

@RunWith(PowerMockRunner.class)
@PowerMockIgnore({"com.sun.org.apache.xerces.*", "javax.xml.*", "org.xml.*", "javax.management.*", "org.w3c.*"})
public class TraceSegmentReportServletHandlerTest {

    @Mock
    private HttpServletRequest request;
    @Mock
    private ModuleManager moduleManager;
    @Mock
    private NoneTelemetryProvider telemetryProvider;

    @Mock
    private AnalyzerModuleProvider analyzerModuleProvider;

    @Before
    public void init() throws IOException {
        TelemetryModule telemetryModule = Mockito.spy(TelemetryModule.class);
        Whitebox.setInternalState(telemetryModule, "loadedProvider", telemetryProvider);
        Mockito.when(moduleManager.find(TelemetryModule.NAME)).thenReturn(telemetryModule);

        AnalyzerModule analyzerModule = Mockito.spy(AnalyzerModule.class);
        Whitebox.setInternalState(analyzerModule, "loadedProvider", analyzerModuleProvider);
        Mockito.when(moduleManager.find(AnalyzerModule.NAME)).thenReturn(analyzerModule);

        Mockito.when(telemetryProvider.getService(MetricsCreator.class))
               .thenReturn(new MetricsCreatorNoop());

        when(analyzerModuleProvider.getService(ISegmentParserService.class))
            .thenReturn(new SegmentParserServiceImpl(moduleManager, new AnalyzerModuleConfig()));
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
            new TraceSegmentReportSingleServletHandler(moduleManager);

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
            new TraceSegmentReportListServletHandler(moduleManager);

        when(request.getReader()).thenReturn(new BufferedReader(new StringReader(listJson)));
        final List<SegmentObject> segmentObjects = singleServletHandler.parseSegments(request);
        Assert.assertEquals(segmentObjects.size(), 2);
    }

}
