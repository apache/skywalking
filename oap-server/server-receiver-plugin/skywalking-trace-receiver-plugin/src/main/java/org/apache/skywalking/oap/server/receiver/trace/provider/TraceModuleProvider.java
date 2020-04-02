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

package org.apache.skywalking.oap.server.receiver.trace.provider;

import org.apache.skywalking.oap.server.configuration.api.ConfigurationModule;
import org.apache.skywalking.oap.server.configuration.api.DynamicConfigurationService;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.server.GRPCHandlerRegister;
import org.apache.skywalking.oap.server.core.server.JettyHandlerRegister;
import org.apache.skywalking.oap.server.library.module.ModuleConfig;
import org.apache.skywalking.oap.server.library.module.ModuleDefine;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.module.ServiceNotProvidedException;
import org.apache.skywalking.oap.server.receiver.sharing.server.SharingServerModule;
import org.apache.skywalking.oap.server.receiver.trace.module.TraceModule;
import org.apache.skywalking.oap.server.receiver.trace.provider.handler.v8.grpc.TraceSegmentReportServiceHandler;
import org.apache.skywalking.oap.server.receiver.trace.provider.handler.v8.rest.TraceSegmentReportServletHandler;
import org.apache.skywalking.oap.server.receiver.trace.provider.parser.ISegmentParserService;
import org.apache.skywalking.oap.server.receiver.trace.provider.parser.SegmentParserListenerManager;
import org.apache.skywalking.oap.server.receiver.trace.provider.parser.SegmentParserServiceImpl;
import org.apache.skywalking.oap.server.receiver.trace.provider.parser.listener.MultiScopesAnalysisListener;
import org.apache.skywalking.oap.server.receiver.trace.provider.parser.listener.NetworkAddressAliasMappingListener;
import org.apache.skywalking.oap.server.receiver.trace.provider.parser.listener.SegmentAnalysisListener;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;

public class TraceModuleProvider extends ModuleProvider {

    private final TraceServiceModuleConfig moduleConfig;
    private DBLatencyThresholdsAndWatcher thresholds;
    private UninstrumentedGatewaysConfig uninstrumentedGatewaysConfig;

    public TraceModuleProvider() {
        this.moduleConfig = new TraceServiceModuleConfig();
    }

    @Override
    public String name() {
        return "default";
    }

    @Override
    public Class<? extends ModuleDefine> module() {
        return TraceModule.class;
    }

    @Override
    public ModuleConfig createConfigBeanIfAbsent() {
        return moduleConfig;
    }

    @Override
    public void prepare() throws ServiceNotProvidedException {
        thresholds = new DBLatencyThresholdsAndWatcher(moduleConfig.getSlowDBAccessThreshold(), this);

        uninstrumentedGatewaysConfig = new UninstrumentedGatewaysConfig(this);

        moduleConfig.setDbLatencyThresholdsAndWatcher(thresholds);
        moduleConfig.setUninstrumentedGatewaysConfig(uninstrumentedGatewaysConfig);

        this.registerServiceImplementation(
            ISegmentParserService.class, new SegmentParserServiceImpl(getManager(), listenerManager(), moduleConfig));
    }

    @Override
    public void start() throws ModuleStartException {
        DynamicConfigurationService dynamicConfigurationService = getManager().find(ConfigurationModule.NAME)
                                                                              .provider()
                                                                              .getService(
                                                                                  DynamicConfigurationService.class);
        GRPCHandlerRegister grpcHandlerRegister = getManager().find(SharingServerModule.NAME)
                                                              .provider()
                                                              .getService(GRPCHandlerRegister.class);
        JettyHandlerRegister jettyHandlerRegister = getManager().find(SharingServerModule.NAME)
                                                                .provider()
                                                                .getService(JettyHandlerRegister.class);
        dynamicConfigurationService.registerConfigChangeWatcher(thresholds);
        dynamicConfigurationService.registerConfigChangeWatcher(uninstrumentedGatewaysConfig);

        grpcHandlerRegister.addHandler(
            new TraceSegmentReportServiceHandler(getManager(), listenerManager(), moduleConfig));

        jettyHandlerRegister.addHandler(
            new TraceSegmentReportServletHandler(getManager(), listenerManager(), moduleConfig));
    }

    @Override
    public void notifyAfterCompleted() {

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

    private SegmentParserListenerManager listenerManager() {
        SegmentParserListenerManager listenerManager = new SegmentParserListenerManager();
        if (moduleConfig.isTraceAnalysis()) {
            listenerManager.add(new MultiScopesAnalysisListener.Factory());
            listenerManager.add(new NetworkAddressAliasMappingListener.Factory());
        }
        listenerManager.add(new SegmentAnalysisListener.Factory(moduleConfig.getSampleRate()));

        return listenerManager;
    }
}
