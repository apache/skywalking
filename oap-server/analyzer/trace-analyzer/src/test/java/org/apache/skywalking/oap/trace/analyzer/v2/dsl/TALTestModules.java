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

package org.apache.skywalking.oap.trace.analyzer.v2.dsl;

import org.apache.skywalking.oap.server.core.analysis.meter.MeterSystem;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.source.SourceReceiver;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleProviderHolder;
import org.apache.skywalking.oap.server.library.module.ModuleServiceHolder;
import org.apache.skywalking.oap.trace.analyzer.v2.dsl.spec.FilterSpec;
import org.apache.skywalking.oap.trace.analyzer.v2.dsl.spec.RateLimitingSamplers;
import org.apache.skywalking.oap.trace.analyzer.v2.module.TraceAnalyzerModule;
import org.apache.skywalking.oap.trace.analyzer.v2.provider.TraceAnalyzerModuleConfig;
import org.apache.skywalking.oap.trace.analyzer.v2.provider.span.AliasRegistrar;
import org.apache.skywalking.oap.trace.analyzer.v2.provider.span.SpanPairing;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Stubs the services a {@link FilterSpec} looks up, so a test builds its spec through the module lookups production
 * makes.
 */
public final class TALTestModules {
    private TALTestModules() {
    }

    /**
     * As {@link #stubServices}, then the spec a rule runs with.
     *
     * @return a spec over those services
     */
    public static FilterSpec filterSpec(final ModuleManager moduleManager,
                                        final ModuleServiceHolder coreProvider,
                                        final MeterSystem meterSystem,
                                        final NamingControl namingControl,
                                        final SourceReceiver sourceReceiver) {
        stubServices(moduleManager, coreProvider, meterSystem, namingControl, sourceReceiver);
        return new FilterSpec(moduleManager, new TraceAnalyzerModuleConfig());
    }

    /**
     * Serve the spec's core services from the core module's stubbed services, and the trace analyzer's own, a fresh
     * pairing buffer whose alias registrar sends to the given receiver and a fresh rate-limit set, as the module
     * provider registers them.
     *
     * @param moduleManager  the mocked manager
     * @param coreProvider   the stubbed services of the core module, which {@code moduleManager} already finds
     * @param meterSystem    the meter system the core serves
     * @param namingControl  the naming control the core serves
     * @param sourceReceiver the source receiver the core serves
     */
    public static void stubServices(final ModuleManager moduleManager,
                                    final ModuleServiceHolder coreProvider,
                                    final MeterSystem meterSystem,
                                    final NamingControl namingControl,
                                    final SourceReceiver sourceReceiver) {
        when(coreProvider.getService(MeterSystem.class)).thenReturn(meterSystem);
        when(coreProvider.getService(NamingControl.class)).thenReturn(namingControl);
        when(coreProvider.getService(SourceReceiver.class)).thenReturn(sourceReceiver);
        final SpanPairing spanPairing = new SpanPairing();
        spanPairing.setAliasRegistrar(new AliasRegistrar(sourceReceiver, namingControl));
        final ModuleServiceHolder analyzerProvider = mock(ModuleServiceHolder.class);
        when(analyzerProvider.getService(SpanPairing.class)).thenReturn(spanPairing);
        when(analyzerProvider.getService(RateLimitingSamplers.class)).thenReturn(new RateLimitingSamplers());
        final ModuleProviderHolder analyzerModule = mock(ModuleProviderHolder.class);
        when(analyzerModule.provider()).thenReturn(analyzerProvider);
        when(moduleManager.find(TraceAnalyzerModule.NAME)).thenReturn(analyzerModule);
    }
}
