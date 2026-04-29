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

package org.apache.skywalking.oap.log.analyzer.v2.provider;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import org.apache.skywalking.oap.log.analyzer.v2.module.LogAnalyzerModule;
import org.apache.skywalking.oap.log.analyzer.v2.provider.log.ILogAnalyzerService;
import org.apache.skywalking.oap.log.analyzer.v2.provider.log.LogAnalyzerServiceImpl;
import org.apache.skywalking.oap.log.analyzer.v2.provider.log.listener.LogFilterListener;
import org.apache.skywalking.oap.meter.analyzer.v2.MalConverterRegistry;
import org.apache.skywalking.oap.meter.analyzer.v2.MetricConvert;
import org.apache.skywalking.oap.server.configuration.api.ConfigurationModule;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterSystem;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.library.module.ModuleDefine;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.module.ServiceNotProvidedException;

/**
 * Owns the analyzer-side state for both rule catalogs whose runtime is hosted in this
 * module:
 * <ul>
 *   <li>{@code lal} — the {@link LogFilterListener.Factory} registry of compiled LAL
 *       rules. The factory is registered as a service so the runtime-rule plugin
 *       reaches it by service class without a compile-time dep.</li>
 *   <li>{@code log-mal-rules} — the volatile {@link Map} of active inline-MAL
 *       converters keyed by {@code "log-mal-rules:<name>"}, plus the
 *       {@link MalConverterRegistry} service the runtime-rule plugin uses to
 *       hot-mutate that map.</li>
 * </ul>
 *
 * <p>Boot path: {@link #prepare()} constructs the LAL factory and the
 * {@link MalConverterRegistry} service; {@link #start()} loads the static
 * {@code log-mal-rules} files into the converter map and the static {@code lal}
 * files into the filter factory, after which both registries are open for runtime
 * mutation. Both static and runtime-rule entries share the same key scheme, so an
 * operator override of a shipped rule lands in place — without a separate "delete
 * the bundled file first" step.
 *
 * <p>The provider also exposes {@link ILogAnalyzerService} so the OTel-log /
 * SkyWalking-native log receivers can dispatch parsed records into LAL.
 */
public class LogAnalyzerModuleProvider extends ModuleProvider {
    @Getter
    private LogAnalyzerModuleConfig moduleConfig;

    /**
     * Active inline-MAL converters ({@code log-mal-rules} catalog — metrics extracted from
     * logs), keyed by a stable handle so runtime-rule hot-update can replace or drop
     * individual entries without touching the others. All entries — both boot-loaded static
     * rules and runtime-rule pushes — share the {@code "log-mal-rules:<name>"} key scheme
     * (boot-time loading happens in {@link #start()} from {@code moduleConfig.malConfigs()};
     * runtime mutations come through {@link MalConverterRegistry} which delegates to
     * {@link #addOrReplaceMetricConvert} / {@link #removeMetricConvert}). A runtime
     * {@code /addOrUpdate} replaces the entry in place over whichever rule (boot or prior
     * runtime push) occupies that key; {@code /inactivate} drops it. This is what lets an
     * operator override a shipped log-mal rule without first deleting it — the update lands
     * under the same key and takes over dispatch.
     *
     * <p>Volatile + copy-on-write: readers in {@link org.apache.skywalking.oap.log.analyzer.v2.dsl.spec.extractor.MetricExtractor} observe a consistent
     * snapshot without a lock; writers replace the map reference under {@link #convertersWriteLock}.
     */
    private volatile Map<String, MetricConvert> metricConverts = Collections.emptyMap();
    private final Object convertersWriteLock = new Object();

    private LogAnalyzerServiceImpl logAnalyzerService;
    private LogFilterListener.Factory factory;

    @Override
    public String name() {
        return "default";
    }

    @Override
    public Class<? extends ModuleDefine> module() {
        return LogAnalyzerModule.class;
    }

    @Override
    public ConfigCreator newConfigCreator() {
        return new ConfigCreator<LogAnalyzerModuleConfig>() {
            @Override
            public Class type() {
                return LogAnalyzerModuleConfig.class;
            }

            @Override
            public void onInitialized(final LogAnalyzerModuleConfig initialized) {
                moduleConfig = initialized;
            }
        };
    }

    @Override
    public void prepare() throws ServiceNotProvidedException, ModuleStartException {
        logAnalyzerService = new LogAnalyzerServiceImpl(getManager(), moduleConfig);
        this.registerServiceImplementation(ILogAnalyzerService.class, logAnalyzerService);

        // Register both module-declared services in prepare(): {@link BootstrapFlow#start}
        // runs {@code requiredCheck} against {@code services()} BEFORE this provider's
        // {@code start()}, and the count-equals check ({@code requiredServices.length ==
        // services.size()}) requires every declared service to be registered already.
        // Both objects are config-only at construction — the Factory's heavy
        // rule-compile pass is deferred to {@link LogFilterListener.Factory#loadStaticRules}
        // (called from {@link #start()} where moduleManager.find is allowed); the
        // MalConverterRegistry is a pure delegate to this provider's own volatile map and
        // never reaches across modules.
        try {
            this.factory = new LogFilterListener.Factory(getManager(), moduleConfig);
            this.registerServiceImplementation(LogFilterListener.Factory.class, this.factory);
        } catch (final Exception e) {
            throw new ModuleStartException("Failed to create LAL listener factory.", e);
        }
        // MalConverterRegistry for the log-mal-rules catalog. The runtime-rule plugin looks
        // this up by module name + service class, so it does not need a compile-time dep on
        // log-analyzer's concrete provider. Delegates directly to this provider's volatile
        // map so ingest code (MetricExtractor) and runtime mutations share exactly one state.
        this.registerServiceImplementation(MalConverterRegistry.class, new MalConverterRegistry() {
            @Override
            public void addOrReplaceConverter(final String key, final MetricConvert convert) {
                addOrReplaceMetricConvert(key, convert);
            }

            @Override
            public void removeConverter(final String key) {
                removeMetricConvert(key);
            }
        });
    }

    @Override
    public void start() throws ServiceNotProvidedException, ModuleStartException {
        MeterSystem meterSystem = getManager().find(CoreModule.NAME).provider().getService(MeterSystem.class);
        for (final var rule : moduleConfig.malConfigs()) {
            // Use the catalog:name key convention so a runtime-rule /addOrUpdate for the same
            // (catalog, name) replaces this static entry in place instead of running two
            // converters against the same sample stream.
            addOrReplaceMetricConvert("log-mal-rules:" + rule.getName(), new MetricConvert(rule, meterSystem));
        }
        try {
            // Light up the Factory now that all peer modules are past prepare:
            // loadStaticRules calls compile() which constructs RecordSinkListener.Factory
            // which calls moduleManager.find() — only safe outside prepare.
            this.factory.loadStaticRules();
            logAnalyzerService.addListenerFactory(this.factory);
        } catch (final Exception e) {
            throw new ModuleStartException("Failed to load static LAL rules.", e);
        }
    }

    @Override
    public void notifyAfterCompleted() throws ServiceNotProvidedException {

    }

    /**
     * Live snapshot of active MAL converters for {@code log-mal-rules}. Consumed by
     * {@link org.apache.skywalking.oap.log.analyzer.v2.dsl.spec.extractor.MetricExtractor} at
     * ingest time. The returned collection is a read-only view; concurrent updates from
     * {@link #addOrReplaceMetricConvert} / {@link #removeMetricConvert} do not invalidate
     * in-flight iteration because writers publish a new map reference rather than mutating
     * the one this method returned.
     */
    public Collection<MetricConvert> getMetricConverts() {
        return metricConverts.values();
    }

    /**
     * Install or replace a single inline-MAL converter identified by {@code key}. Thread-safe
     * against concurrent readers and other writers; readers observe either the pre-call or the
     * post-call snapshot, never a torn intermediate state. Called by the runtime-rule plugin
     * when an operator's {@code /addOrUpdate} commits a new bundle under the
     * {@code log-mal-rules} catalog; boot-time loading also uses this method so there is
     * exactly one installation path.
     */
    public void addOrReplaceMetricConvert(final String key, final MetricConvert convert) {
        synchronized (convertersWriteLock) {
            final Map<String, MetricConvert> copy = new LinkedHashMap<>(metricConverts);
            copy.put(key, convert);
            metricConverts = Collections.unmodifiableMap(copy);
        }
    }

    /**
     * Drop the inline-MAL converter previously installed under {@code key}. No-op if the key
     * is not present — {@code /delete} on a runtime rule that already tore down on this node
     * shouldn't surface an error.
     */
    public void removeMetricConvert(final String key) {
        synchronized (convertersWriteLock) {
            if (!metricConverts.containsKey(key)) {
                return;
            }
            final Map<String, MetricConvert> copy = new LinkedHashMap<>(metricConverts);
            copy.remove(key);
            metricConverts = Collections.unmodifiableMap(copy);
        }
    }

    @Override
    public String[] requiredModules() {
        // StorageModule must start before this provider so the runtime_rule management
        // table is materialised by the time LALConfigs.load and Rules.loadRules consult
        // the RuntimeRuleOverrideResolver chain (the DB-backed resolver needs a query-
        // ready DAO to contribute overrides).
        return new String[] {
            CoreModule.NAME,
            ConfigurationModule.NAME,
            StorageModule.NAME
        };
    }
}
