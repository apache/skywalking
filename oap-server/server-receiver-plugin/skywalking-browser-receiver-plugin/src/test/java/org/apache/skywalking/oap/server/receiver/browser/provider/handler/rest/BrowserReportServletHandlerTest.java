/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.skywalking.oap.server.receiver.browser.provider.handler.rest;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import javax.servlet.http.HttpServletRequest;
import org.apache.skywalking.apm.network.language.agent.v3.BrowserErrorLog;
import org.apache.skywalking.apm.network.language.agent.v3.BrowserPerfData;
import org.apache.skywalking.apm.network.language.agent.v3.ErrorCategory;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.util.ProtoBufJsonUtils;
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

import static org.mockito.Mockito.when;

@RunWith(PowerMockRunner.class)
@PowerMockIgnore({"javax.management.*"})
public class BrowserReportServletHandlerTest {
    @Mock
    private HttpServletRequest request;
    @Mock
    private ModuleManager moduleManager;
    @Mock
    private NoneTelemetryProvider telemetryProvider;

    @Before
    public void init() {
        Mockito.when(telemetryProvider.getService(MetricsCreator.class))
               .thenReturn(new MetricsCreatorNoop());

        TelemetryModule telemetryModule = Mockito.spy(TelemetryModule.class);
        Whitebox.setInternalState(telemetryModule, "loadedProvider", telemetryProvider);
        Mockito.when(moduleManager.find(TelemetryModule.NAME)).thenReturn(telemetryModule);
    }

    @Test
    public void testPerfData() throws IOException {
        BrowserPerfData browserPerfData = BrowserPerfData.newBuilder()
                                                         .setService("test")
                                                         .setServiceVersion("v0.0.1")
                                                         .setPagePath("/e2e-browser")
                                                         .setRedirectTime(10)
                                                         .setDnsTime(10)
                                                         .setTtfbTime(10)
                                                         .setTcpTime(10)
                                                         .setTransTime(10)
                                                         .setDomAnalysisTime(10)
                                                         .setFptTime(10)
                                                         .setDomReadyTime(10)
                                                         .setLoadPageTime(10)
                                                         .setResTime(10)
                                                         .setSslTime(10)
                                                         .setTtlTime(10)
                                                         .setFirstPackTime(10)
                                                         .setFmpTime(10)
                                                         .build();
        final BrowserPerfDataReportServletHandler reportServletHandler = new BrowserPerfDataReportServletHandler(
            moduleManager, null, null);

        when(request.getReader()).thenReturn(
            new BufferedReader(new StringReader(ProtoBufJsonUtils.toJSON(browserPerfData))));
        final BrowserPerfData result = reportServletHandler.parseBrowserPerfData(request);
        Assert.assertEquals(result, browserPerfData);
    }

    @Test
    public void testErrorLogSingle() throws IOException {
        BrowserErrorLog errorLog = BrowserErrorLog.newBuilder()
                                                  .setUniqueId(UUID.randomUUID().toString())
                                                  .setService("test")
                                                  .setServiceVersion("v0.0.1")
                                                  .setPagePath("/e2e-browser")
                                                  .setCategory(ErrorCategory.ajax)
                                                  .setMessage("test")
                                                  .setLine(1)
                                                  .setCol(1)
                                                  .setStack("e2e")
                                                  .setErrorUrl("/e2e-browser").build();

        final BrowserErrorLogReportSingleServletHandler singleServletHandler = new BrowserErrorLogReportSingleServletHandler(
            moduleManager, null, null);

        when(request.getReader()).thenReturn(
            new BufferedReader(new StringReader(ProtoBufJsonUtils.toJSON(errorLog))));
        final List<BrowserErrorLog> browserErrorLogs = singleServletHandler.parseBrowserErrorLog(request);
        Assert.assertEquals(1, browserErrorLogs.size());
        Assert.assertEquals(errorLog, browserErrorLogs.get(0));
    }

    @Test
    public void testErrorLogList() throws IOException {
        List<BrowserErrorLog> errorLogs = Arrays.asList(
            BrowserErrorLog.newBuilder()
                           .setUniqueId("1")
                           .setService("test")
                           .setServiceVersion("v0.0.1")
                           .setPagePath("/e2e-browser")
                           .setCategory(ErrorCategory.ajax)
                           .setMessage("test")
                           .setLine(1)
                           .setCol(1)
                           .setStack("e2e")
                           .setErrorUrl("/e2e-browser").build(),
            BrowserErrorLog.newBuilder()
                           .setUniqueId("2")
                           .setService("test")
                           .setServiceVersion("v0.0.1")
                           .setPagePath("/e2e-browser")
                           .setCategory(ErrorCategory.ajax)
                           .setMessage("test")
                           .setLine(1)
                           .setCol(1)
                           .setStack("e2e")
                           .setErrorUrl("/e2e-browser").build()
        );

        final BrowserErrorLogReportListServletHandler listServletHandler = new BrowserErrorLogReportListServletHandler(
            moduleManager, null, null);

        when(request.getReader()).thenReturn(
            new BufferedReader(new StringReader(
                "[" + ProtoBufJsonUtils.toJSON(errorLogs.get(0)) + "," +
                    ProtoBufJsonUtils.toJSON(errorLogs.get(1)) + "]")));

        final List<BrowserErrorLog> browserErrorLogs = listServletHandler.parseBrowserErrorLog(request);
        Assert.assertEquals(2, browserErrorLogs.size());
        Assert.assertEquals(errorLogs, browserErrorLogs);
    }
}
