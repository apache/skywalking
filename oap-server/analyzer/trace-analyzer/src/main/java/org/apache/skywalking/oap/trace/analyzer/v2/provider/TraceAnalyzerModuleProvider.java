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

package org.apache.skywalking.oap.trace.analyzer.v2.provider;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.analyzer.module.AnalyzerModule;
import org.apache.skywalking.oap.server.analyzer.provider.trace.UninstrumentedGatewaysConfig;
import org.apache.skywalking.oap.server.configuration.api.ConfigurationModule;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.analysis.worker.MetricStreamKind;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.query.MetadataQueryService;
import org.apache.skywalking.oap.server.core.query.type.Service;
import org.apache.skywalking.oap.server.core.remote.RemoteSenderService;
import org.apache.skywalking.oap.server.core.remote.client.RemoteClient;
import org.apache.skywalking.oap.server.core.remote.client.RemoteClientManager;
import org.apache.skywalking.oap.server.core.remote.selector.Selector;
import org.apache.skywalking.oap.server.core.source.SourceReceiver;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.worker.IWorkerInstanceSetter;
import org.apache.skywalking.oap.server.library.module.ModuleDefine;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.apache.skywalking.oap.server.library.module.ModuleServiceHolder;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.module.ServiceNotProvidedException;
import org.apache.skywalking.oap.trace.analyzer.v2.dsl.spec.RateLimitingSamplers;
import org.apache.skywalking.oap.trace.analyzer.v2.module.TraceAnalyzerModule;
import org.apache.skywalking.oap.trace.analyzer.v2.provider.span.AliasRegistrar;
import org.apache.skywalking.oap.trace.analyzer.v2.provider.span.ITraceAnalyzerService;
import org.apache.skywalking.oap.trace.analyzer.v2.provider.span.KnownServices;
import org.apache.skywalking.oap.trace.analyzer.v2.provider.span.PairingFact;
import org.apache.skywalking.oap.trace.analyzer.v2.provider.span.PairingFactSender;
import org.apache.skywalking.oap.trace.analyzer.v2.provider.span.PairingWorker;
import org.apache.skywalking.oap.trace.analyzer.v2.provider.span.SeenSpans;
import org.apache.skywalking.oap.trace.analyzer.v2.provider.span.SpanPairing;
import org.apache.skywalking.oap.trace.analyzer.v2.provider.span.TraceAnalyzerServiceImpl;
import org.apache.skywalking.oap.trace.analyzer.v2.provider.span.listener.SpanFilterListener;

/**
 * Owns the {@code tal} catalog: the registry of compiled rules, and the service the receivers call per span.
 *
 * <p>The factory is constructed in {@code prepare()} because the bootstrap checks every declared service is
 * registered before any provider starts; its rule-compiling pass is deferred to {@code start()}, where reaching
 * other modules is allowed.
 */
@Slf4j
public class TraceAnalyzerModuleProvider extends ModuleProvider {
    private static final int STORED_SERVICES_ATTEMPTS = 10;
    private static final long STORED_SERVICES_RETRY_SECONDS = 30L;
    /**
     * Pairing facts held for the cluster while the sending thread catches up; beyond this they are dropped.
     */
    private static final int PAIRING_BUFFER_SIZE = 10_000;

    @Getter
    private TraceAnalyzerModuleConfig moduleConfig;

    private SpanFilterListener.Factory factory;
    private KnownServices knownServices;
    private SpanPairing spanPairing;

    @Override
    public String name() {
        return "default";
    }

    @Override
    public Class<? extends ModuleDefine> module() {
        return TraceAnalyzerModule.class;
    }

    @Override
    public ConfigCreator newConfigCreator() {
        return new ConfigCreator<TraceAnalyzerModuleConfig>() {
            @Override
            public Class type() {
                return TraceAnalyzerModuleConfig.class;
            }

            @Override
            public void onInitialized(final TraceAnalyzerModuleConfig initialized) {
                moduleConfig = initialized;
            }
        };
    }

    @Override
    public void prepare() throws ServiceNotProvidedException, ModuleStartException {
        try {
            this.factory = new SpanFilterListener.Factory(getManager(), moduleConfig);
            this.registerServiceImplementation(SpanFilterListener.Factory.class, this.factory);
        } catch (final Exception e) {
            throw new ModuleStartException("Failed to create the TAL rule factory.", e);
        }
        this.knownServices = new KnownServices();
        this.registerServiceImplementation(KnownServices.class, this.knownServices);
        this.registerServiceImplementation(RateLimitingSamplers.class, new RateLimitingSamplers());
        this.spanPairing = new SpanPairing();
        this.registerServiceImplementation(SpanPairing.class, this.spanPairing);
        this.registerServiceImplementation(ITraceAnalyzerService.class,
            new TraceAnalyzerServiceImpl(getManager(), this.factory, this.knownServices, this.spanPairing,
                new SeenSpans(moduleConfig.getDedupWindow(), moduleConfig.getDedupCapacity())));
    }

    @Override
    public void start() throws ServiceNotProvidedException, ModuleStartException {
        // Other modules are reachable from here on, so the pairing gets its row writer and its cluster channel
        // before the first rule is compiled against it.
        final ModuleServiceHolder core = getManager().find(CoreModule.NAME).provider();
        final AliasRegistrar aliasRegistrar = new AliasRegistrar(
            core.getService(SourceReceiver.class), core.getService(NamingControl.class), this.knownServices);
        if (getManager().has(AnalyzerModule.NAME)) {
            aliasRegistrar.setGateways(getManager().find(AnalyzerModule.NAME).provider()
                                                   .getService(UninstrumentedGatewaysConfig.class)
                                                   ::isAddressConfiguredAsGateway);
        }
        this.spanPairing.setAliasRegistrar(aliasRegistrar);
        core.getService(IWorkerInstanceSetter.class).put(
            PairingWorker.NAME, new PairingWorker(getManager(), this.spanPairing), MetricStreamKind.OAL, PairingFact.class);
        final RemoteClientManager clientManager = core.getService(RemoteClientManager.class);
        final RemoteSenderService sender = core.getService(RemoteSenderService.class);
        final int samplePercent = moduleConfig.getPairingSamplePercent();
        // Facts leave on the sender's own thread; see PairingFactSender for why the receiver thread never waits.
        final PairingFactSender factSender = new PairingFactSender(
            "TAL_PAIRING_FACTS", PAIRING_BUFFER_SIZE,
            fact -> sender.send(PairingWorker.NAME, fact, Selector.HashCode));
        this.spanPairing.setTransport(fact -> {
            // Otherwise the trace's owner node, for the sampled share of traces; both halves of a call sample
            // alike since they share the id.
            if (pairsLocally(clientManager.getRemoteClient())) {
                this.spanPairing.receive(fact);
            } else if (fact.sampleBucket() < samplePercent) {
                factSender.offer(fact);
            }
        });
        try {
            this.factory.loadStaticRules();
        } catch (final Exception e) {
            throw new ModuleStartException("Failed to load the static TAL rules.", e);
        }
    }

    /**
     * @param clients the cluster's nodes as this node sees them
     * @return whether this node is alone, or knows no node yet while the cluster metadata loads, so the pairs meet
     * here. A Receiver-role node does not list itself, so a single client that is not this node is its one
     * Aggregator, where the pairs of every receiver meet.
     */
    static boolean pairsLocally(final List<RemoteClient> clients) {
        return clients.isEmpty() || clients.size() == 1 && clients.get(0).getAddress().isSelf();
    }

    @Override
    public void notifyAfterCompleted() throws ServiceNotProvidedException {
        // Without rules nothing reads the names, see TraceAnalyzerServiceImpl#doAnalysis.
        if (!factory.hasRules()) {
            return;
        }
        // Off the boot thread: the read only shortens the window in which a callee is unknown, so a slow or failed
        // read must not hold up the start, and the spans fill the names in either way.
        final MetadataQueryService metadata = getManager().find(CoreModule.NAME).provider()
                                                          .getService(MetadataQueryService.class);
        final Thread loader = new Thread(() -> {
            // About five minutes of attempts: storage may not answer yet right after the start, a cold BanyanDB longer.
            for (int attempt = 1; attempt <= STORED_SERVICES_ATTEMPTS; attempt++) {
                try {
                    log.info("TAL known services: {} recorded from storage",
                             recordStoredServices(metadata, knownServices));
                    return;
                } catch (final Exception e) {
                    if (attempt == STORED_SERVICES_ATTEMPTS) {
                        log.warn("TAL known services: reading the stored services failed; spans will record them", e);
                        return;
                    }
                    log.info("TAL known services: reading the stored services failed, attempt {} of {}: {}",
                             attempt, STORED_SERVICES_ATTEMPTS, e.getMessage());
                }
                try {
                    TimeUnit.SECONDS.sleep(STORED_SERVICES_RETRY_SECONDS);
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }, "TAL-known-services-loader");
        loader.setDaemon(true);
        loader.start();
    }

    /**
     * Record the services storage already holds, so a restarted node names a callee that has not emitted since,
     * a feature flag service read now and then, by its own name instead of by an address row another group wrote.
     * Only real services of the layer TAL files a span under by default: conjectured, address-named nodes are
     * stored as services too, and are no service a host could name. A service a rule or {@code service.layer} files
     * under another layer is not read, and is known again from its next span. A stored name counts for a day after
     * the read, see {@link KnownServices#recordStored}.
     *
     * @return how many services were recorded
     */
    static int recordStoredServices(final MetadataQueryService metadata,
                                    final KnownServices knownServices) throws IOException {
        int recorded = 0;
        for (final Service service : metadata.listServices(Layer.OTLP_GENERAL.name(), null)) {
            if (service.isNormal()) {
                knownServices.recordStored(service.getName());
                recorded++;
            }
        }
        return recorded;
    }

    @Override
    public String[] requiredModules() {
        // StorageModule must start first: TALConfigs loads through RuleSetMerger, whose runtime-rule resolver reads
        // the management table. Catalog has no TAL entry, so it finds no TAL rule there.
        final List<String> required = new ArrayList<>(
            List.of(CoreModule.NAME, ConfigurationModule.NAME, StorageModule.NAME));
        // The agent analyzer owns gateways.yml, which the alias guard and the rules' isGateway read; it is declared
        // only when loaded, so no address is a gateway with `agent-analyzer.selector: -`. The manager is null only in
        // unit tests.
        if (getManager() != null && getManager().has(AnalyzerModule.NAME)) {
            required.add(AnalyzerModule.NAME);
        }
        return required.toArray(new String[0]);
    }
}
