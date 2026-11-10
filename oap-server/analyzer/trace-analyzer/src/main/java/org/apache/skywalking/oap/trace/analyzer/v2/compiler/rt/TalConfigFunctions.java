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
 */

package org.apache.skywalking.oap.trace.analyzer.v2.compiler.rt;

import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.analyzer.module.AnalyzerModule;
import org.apache.skywalking.oap.server.analyzer.provider.trace.NativeTraceAnalysisConfig;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.config.IComponentLibraryCatalogService;
import org.apache.skywalking.oap.server.core.source.TALFunctionProvider;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.util.genai.GenAIModelMatcher;
import org.apache.skywalking.oap.trace.analyzer.v2.module.TraceAnalyzerModule;

/**
 * The language's functions that the OAP's configuration answers, which every input has: the uninstrumented gateways,
 * the agents without the upstream's real address, the component library, the core's naming rules and the GenAI models
 * of {@code gen-ai-config.yml}.
 */
@Slf4j
public class TalConfigFunctions implements TALFunctionProvider {
    /**
     * The agent analyzer's settings, reached in {@link #bind}; null when that module is not loaded and on the default
     * registry a unit test compiles against, which binds without a module manager.
     */
    private NativeTraceAnalysisConfig analysisConfig;

    @Override
    public String module() {
        return TraceAnalyzerModule.NAME;
    }

    /**
     * @return true: every input has these functions
     */
    @Override
    public boolean supportsInputType(final String inputType) {
        return true;
    }

    @Override
    public void bind(final ModuleManager moduleManager) {
        if (moduleManager != null && moduleManager.has(AnalyzerModule.NAME)) {
            analysisConfig = moduleManager.find(AnalyzerModule.NAME).provider()
                                          .getService(NativeTraceAnalysisConfig.class);
        }
    }

    /**
     * {@code isGateway(address)} — whether the address is listed in the agent analyzer's {@code gateways.yml}: a bare
     * host is also matched at port 80, as the agent analyzer lists an instance that names no port. Always false when
     * the agent analyzer is not loaded.
     *
     * @param h       the run
     * @param address {@code host} or {@code host:port}
     * @return whether the address is an uninstrumented gateway
     */
    public boolean isGateway(final TalRuntimeHelper h, final String address) {
        return h.spec().getAliasRegistrar().isGateway(address);
    }

    /**
     * {@code isNoUpstreamRealAddressAgent(componentId)} — whether the agent analyzer's
     * {@code noUpstreamRealAddressAgents} lists the component, whose exit span's peer is not the real address of the
     * service it calls. Always false when the agent analyzer is not loaded, as {@link #isGateway} is.
     *
     * @param h           the run
     * @param componentId the component of an exit span
     * @return whether the component is listed
     */
    public boolean isNoUpstreamRealAddressAgent(final TalRuntimeHelper h, final int componentId) {
        final NativeTraceAnalysisConfig bound = analysisConfig;
        if (bound != null) {
            return bound.isNoUpstreamRealAddressAgent(componentId);
        }
        return h.spec().getModuleManager().has(AnalyzerModule.NAME)
            && h.spec().service(AnalyzerModule.NAME, NativeTraceAnalysisConfig.class)
                       .isNoUpstreamRealAddressAgent(componentId);
    }

    /**
     * {@code component(name)} — the component id from {@code component-libraries.yml}, matched exactly and then
     * ignoring case, so {@code grpc} as {@code rpc.system} spells it finds {@code GRPC} and {@code postgresql}
     * finds {@code PostgreSQL}.
     *
     * @param h    the run
     * @param name the component name a span attribute carried
     * @return the id, or 0, the Unknown component, when nothing matches
     */
    public int component(final TalRuntimeHelper h, final String name) {
        if (name == null || name.isEmpty()) {
            return 0;
        }
        try {
            return h.spec().service(CoreModule.NAME, IComponentLibraryCatalogService.class)
                    .getComponentIdIgnoreCase(name);
        } catch (final Exception e) {
            log.debug("Unknown component name {} in a TAL rule", name, e);
            return 0;
        }
    }

    // ==================== Naming ====================

    /**
     * The names {@code NamingControl} formats, as the OAL sources and the ids a rule builds expect them.
     *
     * @param h    the run
     * @param name the service name
     * @return the name, cut to the configured length
     */
    public String formatServiceName(final TalRuntimeHelper h, final String name) {
        return h.spec().getNamingControl().formatServiceName(name);
    }

    public String formatInstanceName(final TalRuntimeHelper h, final String name) {
        return h.spec().getNamingControl().formatInstanceName(name);
    }

    public String formatEndpointName(final TalRuntimeHelper h, final String service, final String endpoint) {
        return h.spec().getNamingControl().formatEndpointName(service, endpoint);
    }

    // ==================== GenAI ====================

    /**
     * {@code GenAIModelMatcher.getInstance().match(model)}: the provider {@code GenAIContextResolver.resolve} asks
     * for, and the prices {@code GenAIProviderPrefixMatcher.match} reads, from {@code gen-ai-config.yml}.
     *
     * @param h     the run
     * @param model the model a span names
     * @return the match for the model's longest prefix
     */
    public GenAIModelMatch matchGenAIModel(final TalRuntimeHelper h, final String model) {
        return new GenAIModelMatch(GenAIModelMatcher.getInstance().match(model));
    }
}
