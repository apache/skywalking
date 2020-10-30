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

package org.apache.skywalking.oap.server.analyzer.provider;

import lombok.Getter;
import org.apache.skywalking.oap.server.analyzer.module.AnalyzerModule;
import org.apache.skywalking.oap.server.analyzer.provider.meter.config.MeterConfig;
import org.apache.skywalking.oap.server.analyzer.provider.meter.config.MeterConfigs;
import org.apache.skywalking.oap.server.analyzer.provider.meter.process.IMeterProcessService;
import org.apache.skywalking.oap.server.analyzer.provider.meter.process.MeterProcessService;
import org.apache.skywalking.oap.server.analyzer.provider.trace.DBLatencyThresholdsAndWatcher;
import org.apache.skywalking.oap.server.analyzer.provider.trace.TraceLatencyThresholdsAndWatcher;
import org.apache.skywalking.oap.server.analyzer.provider.trace.TraceSampleRateWatcher;
import org.apache.skywalking.oap.server.analyzer.provider.trace.UninstrumentedGatewaysConfig;
import org.apache.skywalking.oap.server.analyzer.provider.trace.parser.ISegmentParserService;
import org.apache.skywalking.oap.server.analyzer.provider.trace.parser.SegmentParserListenerManager;
import org.apache.skywalking.oap.server.analyzer.provider.trace.parser.SegmentParserServiceImpl;
import org.apache.skywalking.oap.server.analyzer.provider.trace.parser.listener.MultiScopesAnalysisListener;
import org.apache.skywalking.oap.server.analyzer.provider.trace.parser.listener.NetworkAddressAliasMappingListener;
import org.apache.skywalking.oap.server.analyzer.provider.trace.parser.listener.SegmentAnalysisListener;
import org.apache.skywalking.oap.server.configuration.api.ConfigurationModule;
import org.apache.skywalking.oap.server.configuration.api.DynamicConfigurationService;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.oal.rt.CoreOALDefine;
import org.apache.skywalking.oap.server.core.oal.rt.OALEngineLoaderService;
import org.apache.skywalking.oap.server.library.module.ModuleConfig;
import org.apache.skywalking.oap.server.library.module.ModuleDefine;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.module.ServiceNotProvidedException;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;

import java.util.List;

public class AnalyzerModuleProvider extends ModuleProvider {
    @Getter
    private final AnalyzerModuleConfig moduleConfig;
    @Getter
    private DBLatencyThresholdsAndWatcher thresholds;
    @Getter
    private UninstrumentedGatewaysConfig uninstrumentedGatewaysConfig;
    @Getter
    private SegmentParserServiceImpl segmentParserService;
    @Getter
    private TraceSampleRateWatcher traceSampleRateWatcher;
    @Getter
    private TraceLatencyThresholdsAndWatcher traceLatencyThresholdsAndWatcher;

    private List<MeterConfig> meterConfigs;
    @Getter
    private MeterProcessService processService;

    public AnalyzerModuleProvider() {
        this.moduleConfig = new AnalyzerModuleConfig();
    }

    @Override
    public String name() {
        return "default";
    }

    @Override
    public Class<? extends ModuleDefine> module() {
        return AnalyzerModule.class;
    }

    @Override
    public ModuleConfig createConfigBeanIfAbsent() {
        return moduleConfig;
    }

    @Override
    public void prepare() throws ServiceNotProvidedException, ModuleStartException {
        thresholds = new DBLatencyThresholdsAndWatcher(moduleConfig.getSlowDBAccessThreshold(), this);

        uninstrumentedGatewaysConfig = new UninstrumentedGatewaysConfig(this);

        traceSampleRateWatcher = new TraceSampleRateWatcher(this);
        traceLatencyThresholdsAndWatcher = new TraceLatencyThresholdsAndWatcher(this);

        moduleConfig.setDbLatencyThresholdsAndWatcher(thresholds);
        moduleConfig.setUninstrumentedGatewaysConfig(uninstrumentedGatewaysConfig);
        moduleConfig.setTraceSampleRateWatcher(traceSampleRateWatcher);
        moduleConfig.setTraceLatencyThresholdsAndWatcher(traceLatencyThresholdsAndWatcher);

        segmentParserService = new SegmentParserServiceImpl(getManager(), moduleConfig);
        this.registerServiceImplementation(ISegmentParserService.class, segmentParserService);

        meterConfigs = MeterConfigs.loadConfig(moduleConfig.getConfigPath(), moduleConfig.meterAnalyzerActiveFileNames());
        processService = new MeterProcessService(getManager());
        this.registerServiceImplementation(IMeterProcessService.class, processService);
    }

    @Override
    public void start() throws ModuleStartException {
        // load official analysis
        getManager().find(CoreModule.NAME)
                    .provider()
                    .getService(OALEngineLoaderService.class)
                    .load(CoreOALDefine.INSTANCE);

        DynamicConfigurationService dynamicConfigurationService = getManager().find(ConfigurationModule.NAME)
                                                                              .provider()
                                                                              .getService(
                                                                                  DynamicConfigurationService.class);
        dynamicConfigurationService.registerConfigChangeWatcher(thresholds);
        dynamicConfigurationService.registerConfigChangeWatcher(uninstrumentedGatewaysConfig);
        dynamicConfigurationService.registerConfigChangeWatcher(traceSampleRateWatcher);

        segmentParserService.setListenerManager(listenerManager());

        processService.start(meterConfigs);
    }

    @Override
    public void notifyAfterCompleted() {

    }

    @Override
    public String[] requiredModules() {
        return new String[] {
            TelemetryModule.NAME,
            CoreModule.NAME,
            ConfigurationModule.NAME
        };
    }

    private SegmentParserListenerManager listenerManager() {
        SegmentParserListenerManager listenerManager = new SegmentParserListenerManager();
        if (moduleConfig.isTraceAnalysis()) {
            listenerManager.add(new MultiScopesAnalysisListener.Factory(getManager()));
            listenerManager.add(new NetworkAddressAliasMappingListener.Factory(getManager()));
        }
        listenerManager.add(new SegmentAnalysisListener.Factory(getManager(), moduleConfig));

        return listenerManager;
    }
}
