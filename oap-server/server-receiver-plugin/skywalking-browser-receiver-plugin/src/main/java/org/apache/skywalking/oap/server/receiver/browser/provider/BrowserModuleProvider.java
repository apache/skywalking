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

package org.apache.skywalking.oap.server.receiver.browser.provider;

import org.apache.skywalking.oap.server.configuration.api.ConfigurationModule;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.oal.rt.OALEngineLoaderService;
import org.apache.skywalking.oap.server.core.server.GRPCHandlerRegister;
import org.apache.skywalking.oap.server.core.server.JettyHandlerRegister;
import org.apache.skywalking.oap.server.library.module.ModuleConfig;
import org.apache.skywalking.oap.server.library.module.ModuleDefine;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.module.ServiceNotProvidedException;
import org.apache.skywalking.oap.server.receiver.browser.module.BrowserModule;
import org.apache.skywalking.oap.server.receiver.browser.provider.handler.grpc.BrowserPerfServiceHandlerCompat;
import org.apache.skywalking.oap.server.receiver.browser.provider.handler.grpc.BrowserPerfServiceHandler;
import org.apache.skywalking.oap.server.receiver.browser.provider.handler.rest.BrowserErrorLogReportListServletHandler;
import org.apache.skywalking.oap.server.receiver.browser.provider.handler.rest.BrowserErrorLogReportSingleServletHandler;
import org.apache.skywalking.oap.server.receiver.browser.provider.handler.rest.BrowserPerfDataReportServletHandler;
import org.apache.skywalking.oap.server.receiver.browser.provider.parser.errorlog.ErrorLogParserListenerManager;
import org.apache.skywalking.oap.server.receiver.browser.provider.parser.errorlog.listener.ErrorLogRecordListener;
import org.apache.skywalking.oap.server.receiver.browser.provider.parser.errorlog.listener.MultiScopesErrorLogAnalysisListener;
import org.apache.skywalking.oap.server.receiver.browser.provider.parser.performance.PerfDataParserListenerManager;
import org.apache.skywalking.oap.server.receiver.browser.provider.parser.performance.listener.MultiScopesPerfDataAnalysisListener;
import org.apache.skywalking.oap.server.receiver.sharing.server.SharingServerModule;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;

public class BrowserModuleProvider extends ModuleProvider {
    private final BrowserServiceModuleConfig moduleConfig = new BrowserServiceModuleConfig();

    @Override
    public String name() {
        return "default";
    }

    @Override
    public Class<? extends ModuleDefine> module() {
        return BrowserModule.class;
    }

    @Override
    public ModuleConfig createConfigBeanIfAbsent() {
        return moduleConfig;
    }

    @Override
    public void prepare() throws ServiceNotProvidedException, ModuleStartException {

    }

    @Override
    public void start() throws ServiceNotProvidedException, ModuleStartException {
        // load browser analysis
        getManager().find(CoreModule.NAME)
                    .provider()
                    .getService(OALEngineLoaderService.class)
                    .load(BrowserOALDefine.INSTANCE);

        GRPCHandlerRegister grpcHandlerRegister = getManager().find(SharingServerModule.NAME)
                                                              .provider().getService(GRPCHandlerRegister.class);
        // grpc
        BrowserPerfServiceHandler browserPerfServiceHandler = new BrowserPerfServiceHandler(
            getManager(), moduleConfig, perfDataListenerManager(), errorLogListenerManager());
        grpcHandlerRegister.addHandler(browserPerfServiceHandler);
        grpcHandlerRegister.addHandler(new BrowserPerfServiceHandlerCompat(browserPerfServiceHandler));

        // rest
        JettyHandlerRegister jettyHandlerRegister = getManager().find(SharingServerModule.NAME)
                                                                .provider()
                                                                .getService(JettyHandlerRegister.class);
        // performance
        jettyHandlerRegister.addHandler(
            new BrowserPerfDataReportServletHandler(getManager(), moduleConfig, perfDataListenerManager()));
        // error log
        ErrorLogParserListenerManager errorLogParserListenerManager = errorLogListenerManager();
        jettyHandlerRegister.addHandler(
            new BrowserErrorLogReportSingleServletHandler(getManager(), moduleConfig, errorLogParserListenerManager));
        jettyHandlerRegister.addHandler(
            new BrowserErrorLogReportListServletHandler(getManager(), moduleConfig, errorLogParserListenerManager));
    }

    @Override
    public void notifyAfterCompleted() throws ServiceNotProvidedException, ModuleStartException {

    }

    @Override
    public String[] requiredModules() {
        return new String[] {
            TelemetryModule.NAME,
            CoreModule.NAME,
            SharingServerModule.NAME,
            ConfigurationModule.NAME
        };
    }

    private PerfDataParserListenerManager perfDataListenerManager() {
        PerfDataParserListenerManager listenerManager = new PerfDataParserListenerManager();
        listenerManager.add(new MultiScopesPerfDataAnalysisListener.Factory(getManager(), moduleConfig));

        return listenerManager;
    }

    private ErrorLogParserListenerManager errorLogListenerManager() {
        ErrorLogParserListenerManager listenerManager = new ErrorLogParserListenerManager();
        listenerManager.add(new MultiScopesErrorLogAnalysisListener.Factory(getManager(), moduleConfig));
        listenerManager.add(new ErrorLogRecordListener.Factory(getManager(), moduleConfig));

        return listenerManager;
    }
}
