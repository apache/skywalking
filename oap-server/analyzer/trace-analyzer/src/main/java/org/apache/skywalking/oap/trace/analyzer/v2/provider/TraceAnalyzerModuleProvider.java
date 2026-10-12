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

import lombok.Getter;
import org.apache.skywalking.oap.server.configuration.api.ConfigurationModule;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.worker.MetricStreamKind;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.remote.RemoteSenderService;
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
import org.apache.skywalking.oap.trace.analyzer.v2.module.TraceAnalyzerModule;
import org.apache.skywalking.oap.trace.analyzer.v2.provider.span.ITraceAnalyzerService;
import org.apache.skywalking.oap.trace.analyzer.v2.provider.span.AliasRegistrar;
import org.apache.skywalking.oap.trace.analyzer.v2.provider.span.KnownServices;
import org.apache.skywalking.oap.trace.analyzer.v2.provider.span.PairingFact;
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
public class TraceAnalyzerModuleProvider extends ModuleProvider {
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
        this.spanPairing.setAliasRegistrar(new AliasRegistrar(
            core.getService(SourceReceiver.class), core.getService(NamingControl.class), this.knownServices));
        core.getService(IWorkerInstanceSetter.class).put(
            PairingWorker.NAME, new PairingWorker(getManager(), this.spanPairing), MetricStreamKind.OAL, PairingFact.class);
        final RemoteClientManager clientManager = core.getService(RemoteClientManager.class);
        final RemoteSenderService sender = core.getService(RemoteSenderService.class);
        final int samplePercent = moduleConfig.getPairingSamplePercent();
        this.spanPairing.setTransport(fact -> {
            // One node, or none known yet while the cluster metadata loads: pair here. Otherwise the trace's owner
            // node, for the sampled share of traces; both halves of a call sample alike since they share the id.
            if (clientManager.getRemoteClient().size() <= 1) {
                this.spanPairing.receive(fact);
            } else if (fact.sampleBucket() < samplePercent) {
                sender.send(PairingWorker.NAME, fact, Selector.HashCode);
            }
        });
        try {
            this.factory.loadStaticRules();
        } catch (final Exception e) {
            throw new ModuleStartException("Failed to load the static TAL rules.", e);
        }
    }

    @Override
    public void notifyAfterCompleted() throws ServiceNotProvidedException {
    }

    @Override
    public String[] requiredModules() {
        // StorageModule must start first so the runtime_rule management table exists by the time TALConfigs
        // consults the override resolvers while loading the static rules.
        return new String[] {
            CoreModule.NAME,
            ConfigurationModule.NAME,
            StorageModule.NAME
        };
    }
}
