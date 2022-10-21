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

import java.util.List;
import lombok.Getter;
import org.apache.skywalking.oap.server.analyzer.module.AnalyzerModule;
import org.apache.skywalking.oap.server.analyzer.provider.meter.config.MeterConfig;
import org.apache.skywalking.oap.server.analyzer.provider.meter.config.MeterConfigs;
import org.apache.skywalking.oap.server.analyzer.provider.meter.process.IMeterProcessService;
import org.apache.skywalking.oap.server.analyzer.provider.meter.process.MeterProcessService;
import org.apache.skywalking.oap.server.analyzer.provider.trace.CacheReadLatencyThresholdsAndWatcher;
import org.apache.skywalking.oap.server.analyzer.provider.trace.CacheWriteLatencyThresholdsAndWatcher;
import org.apache.skywalking.oap.server.analyzer.provider.trace.DBLatencyThresholdsAndWatcher;
import org.apache.skywalking.oap.server.analyzer.provider.trace.TraceSamplingPolicyWatcher;
import org.apache.skywalking.oap.server.analyzer.provider.trace.UninstrumentedGatewaysConfig;
import org.apache.skywalking.oap.server.analyzer.provider.trace.parser.ISegmentParserService;
import org.apache.skywalking.oap.server.analyzer.provider.trace.parser.SegmentParserListenerManager;
import org.apache.skywalking.oap.server.analyzer.provider.trace.parser.SegmentParserServiceImpl;
import org.apache.skywalking.oap.server.analyzer.provider.trace.parser.listener.EndpointDepFromCrossThreadAnalysisListener;
import org.apache.skywalking.oap.server.analyzer.provider.trace.parser.listener.NetworkAddressAliasMappingListener;
import org.apache.skywalking.oap.server.analyzer.provider.trace.parser.listener.RPCAnalysisListener;
import org.apache.skywalking.oap.server.analyzer.provider.trace.parser.listener.SegmentAnalysisListener;
import org.apache.skywalking.oap.server.analyzer.provider.trace.parser.listener.VirtualServiceAnalysisListener;
import org.apache.skywalking.oap.server.configuration.api.ConfigurationModule;
import org.apache.skywalking.oap.server.configuration.api.DynamicConfigurationService;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.oal.rt.CoreOALDefine;
import org.apache.skywalking.oap.server.core.oal.rt.OALEngineLoaderService;
import org.apache.skywalking.oap.server.library.module.ModuleDefine;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.module.ServiceNotProvidedException;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;

public class AnalyzerModuleProvider extends ModuleProvider {
    @Getter
    private AnalyzerModuleConfig moduleConfig;
    @Getter
    private DBLatencyThresholdsAndWatcher dbLatencyThresholdsAndWatcher;
    private CacheReadLatencyThresholdsAndWatcher cacheReadLatencyThresholdsAndWatcher;
    private CacheWriteLatencyThresholdsAndWatcher cacheWriteLatencyThresholdsAndWatcher;
    @Getter
    private UninstrumentedGatewaysConfig uninstrumentedGatewaysConfig;
    @Getter
    private SegmentParserServiceImpl segmentParserService;
    @Getter
    private TraceSamplingPolicyWatcher traceSamplingPolicyWatcher;

    private List<MeterConfig> meterConfigs;
    @Getter
    private MeterProcessService processService;

    @Override
    public String name() {
        return "default";
    }

    @Override
    public Class<? extends ModuleDefine> module() {
        return AnalyzerModule.class;
    }

    @Override
    public ConfigCreator newConfigCreator() {
        return new ConfigCreator<AnalyzerModuleConfig>() {
            @Override
            public Class type() {
                return AnalyzerModuleConfig.class;
            }

            @Override
            public void onInitialized(final AnalyzerModuleConfig initialized) {
                moduleConfig = initialized;
            }
        };
    }

    @Override
    public void prepare() throws ServiceNotProvidedException, ModuleStartException {
        dbLatencyThresholdsAndWatcher = new DBLatencyThresholdsAndWatcher(moduleConfig.getSlowDBAccessThreshold(), this);
        uninstrumentedGatewaysConfig = new UninstrumentedGatewaysConfig(this);
        traceSamplingPolicyWatcher = new TraceSamplingPolicyWatcher(moduleConfig, this);
        cacheReadLatencyThresholdsAndWatcher = new CacheReadLatencyThresholdsAndWatcher(moduleConfig.getSlowCacheReadThreshold(), this);
        cacheWriteLatencyThresholdsAndWatcher = new CacheWriteLatencyThresholdsAndWatcher(moduleConfig.getSlowCacheWriteThreshold(), this);

        moduleConfig.setDbLatencyThresholdsAndWatcher(dbLatencyThresholdsAndWatcher);
        moduleConfig.setUninstrumentedGatewaysConfig(uninstrumentedGatewaysConfig);
        moduleConfig.setTraceSamplingPolicyWatcher(traceSamplingPolicyWatcher);
        moduleConfig.setCacheReadLatencyThresholdsAndWatcher(cacheReadLatencyThresholdsAndWatcher);
        moduleConfig.setCacheWriteLatencyThresholdsAndWatcher(cacheWriteLatencyThresholdsAndWatcher);

        segmentParserService = new SegmentParserServiceImpl(getManager(), moduleConfig);
        this.registerServiceImplementation(ISegmentParserService.class, segmentParserService);

        meterConfigs = MeterConfigs.loadConfig(
            moduleConfig.getConfigPath(), moduleConfig.meterAnalyzerActiveFileNames());
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
        dynamicConfigurationService.registerConfigChangeWatcher(dbLatencyThresholdsAndWatcher);
        dynamicConfigurationService.registerConfigChangeWatcher(uninstrumentedGatewaysConfig);
        dynamicConfigurationService.registerConfigChangeWatcher(traceSamplingPolicyWatcher);
        dynamicConfigurationService.registerConfigChangeWatcher(cacheReadLatencyThresholdsAndWatcher);
        dynamicConfigurationService.registerConfigChangeWatcher(cacheWriteLatencyThresholdsAndWatcher);

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
            listenerManager.add(new RPCAnalysisListener.Factory(getManager()));
            listenerManager.add(new EndpointDepFromCrossThreadAnalysisListener.Factory(getManager()));
            listenerManager.add(new NetworkAddressAliasMappingListener.Factory(getManager()));
        }
        listenerManager.add(new SegmentAnalysisListener.Factory(getManager(), moduleConfig));
        listenerManager.add(new VirtualServiceAnalysisListener.Factory(getManager()));

        return listenerManager;
    }
}
