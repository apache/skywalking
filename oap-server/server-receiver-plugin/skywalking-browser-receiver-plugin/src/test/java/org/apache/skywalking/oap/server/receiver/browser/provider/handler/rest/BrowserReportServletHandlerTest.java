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
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import org.apache.skywalking.apm.network.language.agent.v3.BrowserErrorLog;
import org.apache.skywalking.apm.network.language.agent.v3.BrowserPerfData;
import org.apache.skywalking.apm.network.language.agent.v3.ErrorCategory;
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
        final String json = "{\n" +
            "  \"service\": \"test\",\n" +
            "  \"serviceVersion\": \"v0.0.1\",\n" +
            "  \"pagePath\": \"/e2e-browser\",\n" +
            "  \"redirectTime\": 1,\n" +
            "  \"dnsTime\": 2,\n" +
            "  \"ttfbTime\": 3,\n" +
            "  \"tcpTime\": 4,\n" +
            "  \"transTime\": 5,\n" +
            "  \"domAnalysisTime\": 6,\n" +
            "  \"fptTime\": 7,\n" +
            "  \"domReadyTime\": 8,\n" +
            "  \"loadPageTime\": 9,\n" +
            "  \"resTime\": 10,\n" +
            "  \"sslTime\": 11,\n" +
            "  \"ttlTime\": 12,\n" +
            "  \"firstPackTime\": 13,\n" +
            "  \"fmpTime\": 14\n" +
            "}";
        final BrowserPerfDataReportServletHandler reportServletHandler = new BrowserPerfDataReportServletHandler(
            moduleManager, null, null);

        when(request.getReader()).thenReturn(
            new BufferedReader(new StringReader(json)));
        final BrowserPerfData result = reportServletHandler.parseBrowserPerfData(request);
        Assert.assertEquals("test", result.getService());
        Assert.assertEquals("v0.0.1", result.getServiceVersion());
        Assert.assertEquals("/e2e-browser", result.getPagePath());
        Assert.assertEquals(1, result.getRedirectTime());
        Assert.assertEquals(2, result.getDnsTime());
        Assert.assertEquals(3, result.getTtfbTime());
        Assert.assertEquals(4, result.getTcpTime());
        Assert.assertEquals(5, result.getTransTime());
        Assert.assertEquals(6, result.getDomAnalysisTime());
        Assert.assertEquals(7, result.getFptTime());
        Assert.assertEquals(8, result.getDomReadyTime());
        Assert.assertEquals(9, result.getLoadPageTime());
        Assert.assertEquals(10, result.getResTime());
        Assert.assertEquals(11, result.getSslTime());
        Assert.assertEquals(12, result.getTtlTime());
        Assert.assertEquals(13, result.getFirstPackTime());
        Assert.assertEquals(14, result.getFmpTime());
    }

    @Test
    public void testErrorLogSingle() throws IOException {
        final String singleJson = "{\n" +
            "  \"uniqueId\": \"55ec6178-3fb7-43ef-899c-a26944407b0e\",\n" +
            "  \"service\": \"test\",\n" +
            "  \"serviceVersion\": \"v0.0.1\",\n" +
            "  \"pagePath\": \"/e2e-browser\",\n" +
            "  \"category\": \"ajax\",\n" +
            "  \"message\": \"test\",\n" +
            "  \"line\": 1,\n" +
            "  \"col\": 1,\n" +
            "  \"stack\": \"e2e\",\n" +
            "  \"errorUrl\": \"/e2e-browser\"\n" +
            "}";

        final BrowserErrorLogReportSingleServletHandler singleServletHandler = new BrowserErrorLogReportSingleServletHandler(
            moduleManager, null, null);

        when(request.getReader()).thenReturn(
            new BufferedReader(new StringReader(singleJson)));
        final List<BrowserErrorLog> browserErrorLogs = singleServletHandler.parseBrowserErrorLog(request);
        Assert.assertEquals(1, browserErrorLogs.size());
        BrowserErrorLog errorLog = browserErrorLogs.get(0);
        Assert.assertEquals("55ec6178-3fb7-43ef-899c-a26944407b0e", errorLog.getUniqueId());
        Assert.assertEquals("test", errorLog.getService());
        Assert.assertEquals("v0.0.1", errorLog.getServiceVersion());
        Assert.assertEquals("/e2e-browser", errorLog.getPagePath());
        Assert.assertEquals(ErrorCategory.ajax, errorLog.getCategory());
        Assert.assertEquals("test", errorLog.getMessage());
        Assert.assertEquals(1, errorLog.getLine());
        Assert.assertEquals(1, errorLog.getCol());
        Assert.assertEquals("e2e", errorLog.getStack());
        Assert.assertEquals("/e2e-browser", errorLog.getErrorUrl());
    }

    @Test
    public void testErrorLogList() throws IOException {
        final String listJson = "[\n" +
            "    {\n" +
            "        \"uniqueId\": \"55ec6178-3fb7-43ef-899c-a26944407b01\",\n" +
            "        \"service\": \"test\",\n" +
            "        \"serviceVersion\": \"v0.0.1\",\n" +
            "        \"pagePath\": \"/e2e-browser\",\n" +
            "        \"category\": \"ajax\",\n" +
            "        \"message\": \"test\",\n" +
            "        \"line\": 1,\n" +
            "        \"col\": 1,\n" +
            "        \"stack\": \"e2e\",\n" +
            "        \"errorUrl\": \"/e2e-browser\"\n" +
            "    },\n" +
            "    {\n" +
            "        \"uniqueId\": \"55ec6178-3fb7-43ef-899c-a26944407b02\",\n" +
            "        \"service\": \"test\",\n" +
            "        \"serviceVersion\": \"v0.0.1\",\n" +
            "        \"pagePath\": \"/e2e-browser\",\n" +
            "        \"category\": \"ajax\",\n" +
            "        \"message\": \"test\",\n" +
            "        \"line\": 1,\n" +
            "        \"col\": 1,\n" +
            "        \"stack\": \"e2e\",\n" +
            "        \"errorUrl\": \"/e2e-browser\"\n" +
            "    }\n" +
            "]";

        final BrowserErrorLogReportListServletHandler listServletHandler = new BrowserErrorLogReportListServletHandler(
            moduleManager, null, null);

        when(request.getReader()).thenReturn(
            new BufferedReader(new StringReader(listJson)));

        final List<BrowserErrorLog> browserErrorLogs = listServletHandler.parseBrowserErrorLog(request);
        Assert.assertEquals(2, browserErrorLogs.size());
        BrowserErrorLog errorLog1 = browserErrorLogs.get(0);
        Assert.assertEquals("55ec6178-3fb7-43ef-899c-a26944407b01", errorLog1.getUniqueId());
        Assert.assertEquals("test", errorLog1.getService());
        Assert.assertEquals("v0.0.1", errorLog1.getServiceVersion());
        Assert.assertEquals("/e2e-browser", errorLog1.getPagePath());
        Assert.assertEquals(ErrorCategory.ajax, errorLog1.getCategory());
        Assert.assertEquals("test", errorLog1.getMessage());
        Assert.assertEquals(1, errorLog1.getLine());
        Assert.assertEquals(1, errorLog1.getCol());
        Assert.assertEquals("e2e", errorLog1.getStack());
        Assert.assertEquals("/e2e-browser", errorLog1.getErrorUrl());
        BrowserErrorLog errorLog2 = browserErrorLogs.get(1);
        Assert.assertEquals("55ec6178-3fb7-43ef-899c-a26944407b02", errorLog2.getUniqueId());
    }
}
