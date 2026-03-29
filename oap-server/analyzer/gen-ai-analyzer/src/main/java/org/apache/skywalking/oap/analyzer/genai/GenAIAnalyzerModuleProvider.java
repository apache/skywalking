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

package org.apache.skywalking.oap.analyzer.genai;

import org.apache.skywalking.oap.analyzer.genai.config.GenAIConfig;
import org.apache.skywalking.oap.analyzer.genai.config.GenAIConfigLoader;
import org.apache.skywalking.oap.analyzer.genai.config.GenAIOALDefine;
import org.apache.skywalking.oap.analyzer.genai.matcher.GenAIProviderPrefixMatcher;
import org.apache.skywalking.oap.analyzer.genai.module.GenAIAnalyzerModule;
import org.apache.skywalking.oap.analyzer.genai.service.GenAIMeterAnalyzer;
import org.apache.skywalking.oap.analyzer.genai.service.IGenAIMeterAnalyzerService;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.oal.rt.OALEngineLoaderService;
import org.apache.skywalking.oap.server.library.module.ModuleConfig;
import org.apache.skywalking.oap.server.library.module.ModuleDefine;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.module.ServiceNotProvidedException;

public class GenAIAnalyzerModuleProvider extends ModuleProvider {

    private GenAIConfig config;

    @Override
    public String name() {
        return "default";
    }

    @Override
    public Class<? extends ModuleDefine> module() {
        return GenAIAnalyzerModule.class;
    }

    @Override
    public ConfigCreator<? extends ModuleConfig> newConfigCreator() {
        return new ConfigCreator<GenAIConfig>() {
            @Override
            public Class<GenAIConfig> type() {
                return GenAIConfig.class;
            }

            @Override
            public void onInitialized(final GenAIConfig initialized) {
                config = initialized;
            }
        };
    }

    @Override
    public void prepare() throws ServiceNotProvidedException, ModuleStartException {
        GenAIConfigLoader loader = new GenAIConfigLoader(config);
        config = loader.loadConfig();
        GenAIProviderPrefixMatcher matcher = GenAIProviderPrefixMatcher.build();

        NamingControl namingControl = getManager().find(CoreModule.NAME)
                .provider()
                .getService(NamingControl.class);

        this.registerServiceImplementation(
                IGenAIMeterAnalyzerService.class,
                new GenAIMeterAnalyzer(matcher,namingControl));
    }

    @Override
    public void start() throws ServiceNotProvidedException, ModuleStartException {
        getManager().find(CoreModule.NAME)
                .provider()
                .getService(OALEngineLoaderService.class)
                .load(GenAIOALDefine.INSTANCE);
    }

    @Override
    public void notifyAfterCompleted() throws ServiceNotProvidedException, ModuleStartException {

    }

    @Override
    public String[] requiredModules() {
        return new String[] {
                CoreModule.NAME
        };
    }
}
