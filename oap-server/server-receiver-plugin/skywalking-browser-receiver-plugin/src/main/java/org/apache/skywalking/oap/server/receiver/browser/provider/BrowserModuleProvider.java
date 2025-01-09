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

import com.linecorp.armeria.common.HttpMethod;
import java.util.Collections;
import org.apache.skywalking.oap.server.configuration.api.ConfigurationModule;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.oal.rt.OALEngineLoaderService;
import org.apache.skywalking.oap.server.core.server.GRPCHandlerRegister;
import org.apache.skywalking.oap.server.core.server.HTTPHandlerRegister;
import org.apache.skywalking.oap.server.library.module.ModuleDefine;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.module.ServiceNotProvidedException;
import org.apache.skywalking.oap.server.receiver.browser.module.BrowserModule;
import org.apache.skywalking.oap.server.receiver.browser.provider.handler.grpc.BrowserPerfServiceHandler;
import org.apache.skywalking.oap.server.receiver.browser.provider.handler.grpc.BrowserPerfServiceHandlerCompat;
import org.apache.skywalking.oap.server.receiver.browser.provider.handler.rest.BrowserPerfServiceHTTPHandler;
import org.apache.skywalking.oap.server.receiver.browser.provider.parser.errorlog.ErrorLogParserListenerManager;
import org.apache.skywalking.oap.server.receiver.browser.provider.parser.errorlog.listener.ErrorLogRecordListener;
import org.apache.skywalking.oap.server.receiver.browser.provider.parser.errorlog.listener.MultiScopesErrorLogAnalysisListener;
import org.apache.skywalking.oap.server.receiver.browser.provider.parser.performance.PerfDataParserListenerManager;
import org.apache.skywalking.oap.server.receiver.browser.provider.parser.performance.decorators.BrowserPerfDataDecorator;
import org.apache.skywalking.oap.server.receiver.browser.provider.parser.performance.decorators.BrowserResourcePerfDataDecorator;
import org.apache.skywalking.oap.server.receiver.browser.provider.parser.performance.decorators.BrowserWebVitalsPerfDataDecorator;
import org.apache.skywalking.oap.server.receiver.browser.provider.parser.performance.listener.BrowserPerfDataAnalysisListener;
import org.apache.skywalking.oap.server.receiver.browser.provider.parser.performance.listener.BrowserWebResourcePerfDataAnalysisListener;
import org.apache.skywalking.oap.server.receiver.browser.provider.parser.performance.listener.BrowserWebVitalsPerfDataAnalysisListener;
import org.apache.skywalking.oap.server.receiver.sharing.server.SharingServerModule;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;

public class BrowserModuleProvider extends ModuleProvider {
    private BrowserServiceModuleConfig moduleConfig;

    @Override
    public String name() {
        return "default";
    }

    @Override
    public Class<? extends ModuleDefine> module() {
        return BrowserModule.class;
    }

    @Override
    public ConfigCreator newConfigCreator() {
        return new ConfigCreator<BrowserServiceModuleConfig>() {
            @Override
            public Class type() {
                return BrowserServiceModuleConfig.class;
            }

            @Override
            public void onInitialized(final BrowserServiceModuleConfig initialized) {
                moduleConfig = initialized;
            }
        };
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
        HTTPHandlerRegister httpHandlerRegister = getManager().find(SharingServerModule.NAME)
                                                              .provider()
                                                              .getService(HTTPHandlerRegister.class);

        ErrorLogParserListenerManager errorLogParserListenerManager = errorLogListenerManager();
        httpHandlerRegister.addHandler(
            new BrowserPerfServiceHTTPHandler(getManager(), moduleConfig,
                                              errorLogParserListenerManager,
                                              perfDataListenerManager()), Collections.singletonList(HttpMethod.POST));
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
        PerfDataParserListenerManager listenerManager = new PerfDataParserListenerManager(getManager(), moduleConfig);
        listenerManager.add(BrowserPerfDataDecorator.class, new BrowserPerfDataAnalysisListener.Factory(getManager(), moduleConfig));
        listenerManager.add(BrowserWebVitalsPerfDataDecorator.class, new BrowserWebVitalsPerfDataAnalysisListener.Factory(getManager(), moduleConfig));
        listenerManager.add(BrowserResourcePerfDataDecorator.class, new BrowserWebResourcePerfDataAnalysisListener.Factory(getManager(), moduleConfig));
        return listenerManager;
    }

    private ErrorLogParserListenerManager errorLogListenerManager() {
        ErrorLogParserListenerManager listenerManager = new ErrorLogParserListenerManager();
        listenerManager.add(new MultiScopesErrorLogAnalysisListener.Factory(getManager(), moduleConfig));
        listenerManager.add(new ErrorLogRecordListener.Factory(getManager(), moduleConfig));

        return listenerManager;
    }
}
