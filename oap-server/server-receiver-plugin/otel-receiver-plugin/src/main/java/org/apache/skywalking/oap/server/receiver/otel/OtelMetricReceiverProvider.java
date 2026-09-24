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

package org.apache.skywalking.oap.server.receiver.otel;

import org.apache.skywalking.oap.server.library.module.ModuleDefine;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.module.ServiceNotProvidedException;
import org.apache.skywalking.oap.meter.analyzer.v2.MalConverterRegistry;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.receiver.otel.otlp.OpenTelemetryMetricRequestProcessor;
import org.apache.skywalking.oap.server.receiver.otel.otlp.OpenTelemetryTraceHandler;
import org.apache.skywalking.oap.server.receiver.zipkin.ZipkinReceiverModule;
import org.apache.skywalking.oap.server.receiver.sharing.server.SharingServerModule;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

public class OtelMetricReceiverProvider extends ModuleProvider {
    public static final String NAME = "default";

    private List<Handler> handlers;

    private OtelMetricReceiverConfig config;

    private OpenTelemetryMetricRequestProcessor metricRequestProcessor;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Class<? extends ModuleDefine> module() {
        return OtelMetricReceiverModule.class;
    }

    @Override
    public ConfigCreator<OtelMetricReceiverConfig> newConfigCreator() {
        return new ConfigCreator<OtelMetricReceiverConfig>() {
            @Override
            public Class<OtelMetricReceiverConfig> type() {
                return OtelMetricReceiverConfig.class;
            }

            @Override
            public void onInitialized(final OtelMetricReceiverConfig initialized) {
                config = initialized;
            }
        };
    }

    @Override
    public void prepare() throws ServiceNotProvidedException, ModuleStartException {
        if (!config.isOtlpTraceStorageValid()) {
            throw new ModuleStartException(
                "receiver-otel.default.otlpTraceStorage must be `" + OtelMetricReceiverConfig.OTLP_TRACE_STORAGE_ZIPKIN
                    + "` or `" + OtelMetricReceiverConfig.OTLP_TRACE_STORAGE_OTLP + "`, got `" + config.getOtlpTraceStorage() + "`");
        }
        // Only OTLPSpanForward, the native path, reads otlpTraceSearchableTags; in zipkin mode the list is dead config
        // and receiver-zipkin.searchableTracesTags applies, so a stale value there must not fail the boot.
        if (config.isOtlpTraceStorageNative() && !config.getUnscopedOtlpTraceSearchableTags().isEmpty()) {
            throw new ModuleStartException(
                "receiver-otel.default.otlpTraceSearchableTags entries must name their scope, `resource.<key>` or "
                    + "`span.<key>`, got " + config.getUnscopedOtlpTraceSearchableTags());
        }
        metricRequestProcessor = new OpenTelemetryMetricRequestProcessor(
            getManager(), config);
        registerServiceImplementation(OpenTelemetryMetricRequestProcessor.class, metricRequestProcessor);
        // Expose the same instance under the MalConverterRegistry contract so the runtime-rule
        // plugin can push / drop otel-rules converters without depending on otel-receiver's
        // concrete processor class.
        registerServiceImplementation(MalConverterRegistry.class, metricRequestProcessor);
        final List<String> enabledHandlers = config.getEnabledHandlers();

        final var handlers = new ArrayList<Handler>();
        for (final var handler: ServiceLoader.load(Handler.class)) {
            if (enabledHandlers.contains(handler.type())) {
                handlers.add(handler);
            }
        }
        this.handlers = handlers;
    }

    @Override
    public void start() throws ServiceNotProvidedException, ModuleStartException {
        metricRequestProcessor.start();
        for (Handler h : handlers) {
            h.init(getManager(), config);
            h.active();
        }
    }

    @Override
    public void notifyAfterCompleted() throws ServiceNotProvidedException, ModuleStartException {
    }

    @Override
    public String[] requiredModules() {
        // StorageModule is declared so Storage.start() (and its catch-up whenCreating
        // fan-out that creates the runtime_rule management table) runs before this
        // provider's start(), guaranteeing the RuntimeRuleOverrideResolver's DB-backed
        // resolver can load during static rule registration. Without this dep the
        // module-system sort could place OTEL ahead of Storage, the resolver would
        // silently no-op at boot, and DB overrides would only take effect on the
        // reconciler's next tick.
        // CoreModule: the trace handler reaches SourceReceiver, NamingControl and SpanListenerManager through the
        // module manager, which is only legal for a declared dependency.
        final List<String> required = new ArrayList<>(List.of(SharingServerModule.NAME, StorageModule.NAME, CoreModule.NAME));
        // The Zipkin mode of otlp-traces hands spans to receiver-zipkin's SpanForwardService; declaring it here makes
        // a boot without that module fail with the module system's own error instead of on the first export, while
        // the native mode keeps not needing it.
        if (config.getEnabledHandlers().contains(OpenTelemetryTraceHandler.TYPE) && !config.isOtlpTraceStorageNative()) {
            required.add(ZipkinReceiverModule.NAME);
        }
        return required.toArray(new String[0]);
    }
}
