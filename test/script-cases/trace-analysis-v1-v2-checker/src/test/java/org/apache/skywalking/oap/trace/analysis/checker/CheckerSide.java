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

package org.apache.skywalking.oap.trace.analysis.checker;

import com.google.common.cache.Cache;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.Set;
import java.util.TreeSet;
import org.apache.skywalking.apm.network.language.agent.v3.SegmentObject;
import org.apache.skywalking.oap.analyzer.genai.matcher.GenAIProviderPrefixMatcher;
import org.apache.skywalking.oap.analyzer.genai.module.GenAIAnalyzerModule;
import org.apache.skywalking.oap.analyzer.genai.service.GenAIMeterAnalyzer;
import org.apache.skywalking.oap.analyzer.genai.service.IGenAIMeterAnalyzerService;
import org.apache.skywalking.oap.server.ai.evaluation.AIEvaluationModule;
import org.apache.skywalking.oap.server.ai.evaluation.AIEvaluationSamplingConfig;
import org.apache.skywalking.oap.server.ai.evaluation.listener.GenAIEvaluationSampleDispatcher;
import org.apache.skywalking.oap.server.ai.evaluation.service.IAIEvaluationService;
import org.apache.skywalking.oap.server.analyzer.module.AnalyzerModule;
import org.apache.skywalking.oap.server.analyzer.provider.AnalyzerModuleConfig;
import org.apache.skywalking.oap.server.analyzer.provider.trace.CacheReadLatencyThresholdsAndWatcher;
import org.apache.skywalking.oap.server.analyzer.provider.trace.CacheWriteLatencyThresholdsAndWatcher;
import org.apache.skywalking.oap.server.analyzer.provider.trace.DBLatencyThresholdsAndWatcher;
import org.apache.skywalking.oap.server.analyzer.provider.trace.NativeTraceAnalysisConfig;
import org.apache.skywalking.oap.server.analyzer.provider.trace.NativeTraceAnalysisConfigImpl;
import org.apache.skywalking.oap.server.analyzer.provider.trace.TraceSamplingPolicyWatcher;
import org.apache.skywalking.oap.server.analyzer.provider.trace.UninstrumentedGatewaysConfig;
import org.apache.skywalking.oap.server.analyzer.provider.trace.parser.listener.strategy.SegmentStatusAnalyzer;
import org.apache.skywalking.oap.server.analyzer.provider.trace.parser.listener.strategy.SegmentStatusStrategy;
import org.apache.skywalking.oap.server.configuration.api.ConfigChangeWatcher;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.CoreModuleConfig;
import org.apache.skywalking.oap.server.core.analysis.manual.networkalias.NetworkAddressAlias;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterSystem;
import org.apache.skywalking.oap.server.core.cache.CheckerAliasCacheLoader;
import org.apache.skywalking.oap.server.core.cache.NetworkAddressAliasCache;
import org.apache.skywalking.oap.server.core.config.ConfigService;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.config.SearchableTracesTagsWatcher;
import org.apache.skywalking.oap.server.core.config.ServerClusterConfig;
import org.apache.skywalking.oap.server.core.config.group.EndpointNameGrouping;
import org.apache.skywalking.oap.server.core.config.group.EndpointNameGroupingRuleWatcher;
import org.apache.skywalking.oap.server.core.config.group.openapi.EndpointNameGroupingRule4OpenapiWatcher;
import org.apache.skywalking.oap.server.core.analysis.DispatcherDetectorListener;
import org.apache.skywalking.oap.server.core.source.GenAIEvaluationSample;
import org.apache.skywalking.oap.server.core.source.ISource;
import org.apache.skywalking.oap.server.core.source.NetworkAddressAliasSetup;
import org.apache.skywalking.oap.server.core.source.SourceReceiver;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleNotFoundRuntimeException;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.apache.skywalking.oap.server.library.module.ModuleProviderHolder;
import org.apache.skywalking.oap.server.library.module.ModuleServiceHolder;
import org.apache.skywalking.oap.server.library.module.Service;
import org.apache.skywalking.oap.server.library.module.ServiceNotProvidedException;
import org.apache.skywalking.oap.trace.analyzer.v2.dsl.spec.RateLimitingSamplers;
import org.apache.skywalking.oap.trace.analyzer.v2.module.TraceAnalyzerModule;
import org.apache.skywalking.oap.trace.analyzer.v2.provider.span.AliasRegistrar;
import org.apache.skywalking.oap.trace.analyzer.v2.provider.span.SpanPairing;

import static org.mockito.Mockito.mock;

/**
 * One side of the comparison: its own instance of every stateful collaborator, kept for a whole multi-segment run as
 * the OAP keeps them, behind a module manager that serves them as the modules do: a module the profile does not load
 * is missing, and a service a loaded module does not register is not provided. Each call the comparison looks at is
 * recorded in the side's event log.
 */
abstract class CheckerSide {
    private static final String ALIAS_CHECK = "alias get(";

    protected final CheckerProfile profile;
    protected final CheckerEventLog log = new CheckerEventLog();
    protected final ModuleManager moduleManager = new ModuleManager("checker") {
        @Override
        public boolean has(final String moduleName) {
            return holders.containsKey(moduleName);
        }

        @Override
        public ModuleProviderHolder find(final String moduleName) {
            final ModuleProviderHolder holder = holders.get(moduleName);
            if (holder == null) {
                throw new ModuleNotFoundRuntimeException(moduleName + " missing.");
            }
            return holder;
        }
    };
    protected final AnalyzerModuleConfig analyzerConfig = new AnalyzerModuleConfig();
    protected final CheckerNamingControl naming;
    protected final SourceReceiver sourceReceiver = new SourceReceiver() {
        @Override
        public void receive(final ISource source) {
            if (source instanceof GenAIEvaluationSample) {
                // ai-evaluation's dispatcher takes it to the evaluation service, as the source receiver does at
                // runtime; the listener called the service itself, so the two sides meet at its sample call.
                GenAIEvaluationSampleDispatcher.bind(evaluationService);
                new GenAIEvaluationSampleDispatcher().dispatch((GenAIEvaluationSample) source);
                sink.accept(source);
                return;
            }
            if (source != null && log.isRecording()) {
                log.event("source " + CheckerObjectRenderer.render(source));
            }
            if (source instanceof NetworkAddressAliasSetup && (collectingAliasSetups || log.isRecording())) {
                aliasSetups.add((NetworkAddressAliasSetup) source);
            }
            sink.accept(source);
        }

        @Override
        public DispatcherDetectorListener getDispatcherDetectorListener() {
            return null;
        }
    };
    protected final NetworkAddressAliasCache aliasCache;
    private final CheckerLogCapture capture;
    private final Map<String, Map<Class<?>, Object>> modules = new HashMap<>();
    private final Map<String, ModuleProviderHolder> holders = new HashMap<>();
    private final List<NetworkAddressAliasSetup> aliasSetups = new ArrayList<>();
    private boolean collectingAliasSetups;
    /**
     * This side's evaluation service; null when the profile does not load ai-evaluation.
     */
    private CheckerEvaluationService evaluationService;
    private Consumer<ISource> sink = source -> {
    };
    private SearchableTracesTagsWatcher searchable;

    CheckerSide(final CheckerProfile profile, final CheckerLogCapture capture) throws Exception {
        this.profile = profile;
        this.capture = capture;
        final ModuleProvider provider = mock(ModuleProvider.class);
        naming = new CheckerNamingControl(profile.getServiceNameMaxLength(), profile.getInstanceNameMaxLength(),
            profile.getEndpointNameMaxLength(), endpointGrouping(provider), log);
        final CoreModuleConfig coreConfig = new CoreModuleConfig();
        setField(coreConfig, "maxSizeOfNetworkAddressAlias", profile.getAliasCacheSize());
        aliasCache = new NetworkAddressAliasCache(coreConfig) {
            @Override
            public NetworkAddressAlias get(final String address) {
                final NetworkAddressAlias alias = super.get(address);
                if (log.isRecording()) {
                    log.event(ALIAS_CHECK + CheckerObjectRenderer.render(address) + ") -> " + CheckerObjectRenderer.render(alias));
                }
                return alias;
            }
        };
        final ServerClusterConfig clusters = new ServerClusterConfig(provider, profile.getServiceNameMaxLength());
        if (profile.getServerClusters() != null) {
            clusters.notify(new ConfigChangeWatcher.ConfigChangeEvent(
                profile.getServerClusters(), ConfigChangeWatcher.EventType.MODIFY));
        }
        searchable = new SearchableTracesTagsWatcher(
            profile.getSearchableTracesTags(), provider) {
            @Override
            public Set<String> getSearchableTags() {
                final Set<String> tags = super.getSearchableTags();
                if (log.isRecording()) {
                    log.event("searchableTags -> " + CheckerObjectRenderer.render(tags == null ? null : new TreeSet<>(tags)));
                }
                return tags;
            }
        };
        final ConfigService configService = new ConfigService(new CoreModuleConfig(), provider) {
            @Override
            public SearchableTracesTagsWatcher getSearchableTracesTags() {
                return searchable;
            }
        };

        configureAnalyzer(provider);
        final UninstrumentedGatewaysConfig gateways = analyzerConfig.getUninstrumentedGatewaysConfig();

        serve(CoreModule.NAME, NamingControl.class, naming);
        serve(CoreModule.NAME, SourceReceiver.class, sourceReceiver);
        serve(CoreModule.NAME, NetworkAddressAliasCache.class, aliasCache);
        serve(CoreModule.NAME, ServerClusterConfig.class, clusters);
        serve(CoreModule.NAME, ConfigService.class, configService);
        serve(CoreModule.NAME, MeterSystem.class, mock(MeterSystem.class));

        serve(AnalyzerModule.NAME, NativeTraceAnalysisConfig.class,
            withRecordedStatusAnalysis(() -> new NativeTraceAnalysisConfigImpl(analyzerConfig)));
        serve(AnalyzerModule.NAME, UninstrumentedGatewaysConfig.class, gateways);

        final SpanPairing pairing = new SpanPairing();
        final AliasRegistrar registrar = new AliasRegistrar(sourceReceiver, naming);
        registrar.setGateways(gateways::isAddressConfiguredAsGateway);
        pairing.setAliasRegistrar(registrar);
        serve(TraceAnalyzerModule.NAME, SpanPairing.class, pairing);
        serve(TraceAnalyzerModule.NAME, RateLimitingSamplers.class, new RateLimitingSamplers());

        if (profile.isGenAiLoaded()) {
            final GenAIMeterAnalyzer genAI = new GenAIMeterAnalyzer(GenAIProviderPrefixMatcher.build());
            genAI.setNamingControl(naming);
            serve(GenAIAnalyzerModule.NAME, IGenAIMeterAnalyzerService.class, genAI);
        }
        if (profile.isEvaluationLoaded()) {
            evaluationService =
                new CheckerEvaluationService(profile.isEvaluationActive(), profile.getEvaluationSampleRate(), log);
            serve(AIEvaluationModule.NAME, IAIEvaluationService.class, evaluationService);
            serve(AIEvaluationModule.NAME, AIEvaluationSamplingConfig.class,
                new AIEvaluationSamplingConfig(profile.isEvaluationActive(), profile.getEvaluationSampleRate()));
        }
        serveModules();
    }

    /**
     * The endpoint grouping with its rules, as {@code CoreModuleProvider.prepare()} sets it up: the custom rules of
     * {@code endpoint-name-grouping.yml}, and the OpenAPI definitions when grouping by them is enabled.
     */
    private EndpointNameGrouping endpointGrouping(final ModuleProvider provider) throws Exception {
        final EndpointNameGrouping grouping = new EndpointNameGrouping();
        // Set as prepareForHTTPUrlRecognition sets it, without starting the recognition it also prepares.
        setField(grouping, "maxHttpUrisNumberPerService", profile.getMaxHttpUrisNumberPerService());
        final EndpointNameGroupingRuleWatcher rules = new EndpointNameGroupingRuleWatcher(provider, grouping);
        if (profile.getEndpointGroupingRules() != null) {
            rules.notify(new ConfigChangeWatcher.ConfigChangeEvent(
                profile.getEndpointGroupingRules(), ConfigChangeWatcher.EventType.MODIFY));
        }
        if (profile.isEndpointNameGroupingByOpenapi()) {
            new EndpointNameGroupingRule4OpenapiWatcher(provider, grouping);
        }
        return grouping;
    }

    /**
     * The agent analyzer's configuration and watchers, as {@code AnalyzerModuleProvider.prepare()} sets them up.
     */
    private void configureAnalyzer(final ModuleProvider provider) throws Exception {
        analyzerConfig.setTraceAnalysis(profile.isTraceAnalysis());
        analyzerConfig.setMaxSlowSQLLength(profile.getMaxSlowSQLLength());
        analyzerConfig.setForceSampleErrorSegment(profile.isForceSampleErrorSegment());
        analyzerConfig.setSlowDBAccessThreshold(profile.getSlowDBAccessThreshold());
        analyzerConfig.setSlowCacheReadThreshold(profile.getSlowCacheReadThreshold());
        analyzerConfig.setSlowCacheWriteThreshold(profile.getSlowCacheWriteThreshold());
        analyzerConfig.setTraceSamplingPolicySettingsFile(profile.getTraceSamplingPolicySettingsFile());
        // Set as the module configuration loader sets them, these two having no setter.
        setField(analyzerConfig, "noUpstreamRealAddressAgents", profile.getNoUpstreamRealAddressAgents());
        setField(analyzerConfig, "segmentStatusAnalysisStrategy", profile.getSegmentStatusAnalysisStrategy());

        analyzerConfig.setDbLatencyThresholdsAndWatcher(
            new DBLatencyThresholdsAndWatcher(analyzerConfig.getSlowDBAccessThreshold(), provider));
        final UninstrumentedGatewaysConfig gateways = new UninstrumentedGatewaysConfig(provider);
        if (profile.getGateways() != null) {
            gateways.notify(new ConfigChangeWatcher.ConfigChangeEvent(
                profile.getGateways(), ConfigChangeWatcher.EventType.MODIFY));
        }
        analyzerConfig.setUninstrumentedGatewaysConfig(gateways);
        final TraceSamplingPolicyWatcher sampling = new TraceSamplingPolicyWatcher(analyzerConfig, provider) {
            @Override
            public boolean shouldSample(final String service, final int sample, final int duration) {
                final boolean sampled = super.shouldSample(service, sample, duration);
                if (log.isRecording()) {
                    log.event("sampler shouldSample(" + CheckerObjectRenderer.render(service) + ", " + sample + ", " + duration
                    + ") -> " + sampled);
                }
                return sampled;
            }
        };
        if (profile.getSamplingPolicy() != null) {
            sampling.notify(new ConfigChangeWatcher.ConfigChangeEvent(
                profile.getSamplingPolicy(), ConfigChangeWatcher.EventType.MODIFY));
        }
        analyzerConfig.setTraceSamplingPolicyWatcher(sampling);
        analyzerConfig.setCacheReadLatencyThresholdsAndWatcher(
            new CacheReadLatencyThresholdsAndWatcher(analyzerConfig.getSlowCacheReadThreshold(), provider));
        analyzerConfig.setCacheWriteLatencyThresholdsAndWatcher(
            new CacheWriteLatencyThresholdsAndWatcher(analyzerConfig.getSlowCacheWriteThreshold(), provider));
    }

    /**
     * Build what {@code construct} builds while every segment status strategy hands out an analyzer that records its
     * calls in this side's log: both sides take the analyzer from the strategy once, when they are built.
     */
    protected final <T> T withRecordedStatusAnalysis(final Callable<T> construct) throws Exception {
        if (!profile.isInstrumented()) {
            return construct.call();
        }
        final Field analyzer = SegmentStatusStrategy.class.getDeclaredField("exceptionAnalyzer");
        analyzer.setAccessible(true);
        final Map<SegmentStatusStrategy, SegmentStatusAnalyzer> originals = new EnumMap<>(SegmentStatusStrategy.class);
        for (final SegmentStatusStrategy strategy : SegmentStatusStrategy.values()) {
            final SegmentStatusAnalyzer original = strategy.getExceptionAnalyzer();
            originals.put(strategy, original);
            final SegmentStatusAnalyzer recorded = span -> {
                final boolean error = original.isError(span);
                if (log.isRecording()) {
                    log.event("status " + strategy.name() + " isError(spanId=" + span.getSpanId() + ", "
                    + CheckerObjectRenderer.render(span.getOperationName()) + ") -> " + error);
                }
                return error;
            };
            analyzer.set(strategy, recorded);
        }
        try {
            return construct.call();
        } finally {
            for (final Map.Entry<SegmentStatusStrategy, SegmentStatusAnalyzer> original : originals.entrySet()) {
                analyzer.set(original.getKey(), original.getValue());
            }
        }
    }

    private void serve(final String module, final Class<?> type, final Object service) {
        modules.computeIfAbsent(module, k -> new HashMap<>()).put(type, service);
    }

    /**
     * Fill the holders the module manager answers {@code has} and {@code find} from, as {@code ModuleManager} does
     * for the modules {@link #serve} filled, each answering {@code getService} as {@code ModuleProvider} does for its
     * services. Plain lookups, as the OAP's, so the side that looks services up per segment pays what the OAP pays.
     */
    private void serveModules() {
        modules.forEach((module, services) -> {
            final ModuleServiceHolder provider = new ModuleServiceHolder() {
                @Override
                public void registerServiceImplementation(final Class<? extends Service> serviceType,
                                                          final Service service) {
                    throw new UnsupportedOperationException("the checker serves a fixed set of services");
                }

                @Override
                @SuppressWarnings("unchecked")
                public <T extends Service> T getService(final Class<T> serviceType) {
                    final Object service = services.get(serviceType);
                    if (service == null) {
                        throw new ServiceNotProvidedException(
                            "Service " + serviceType.getName() + " should not be provided, based on moduleDefine define.");
                    }
                    return (T) service;
                }
            };
            holders.put(module, () -> provider);
        });
    }

    private static void setField(final Object target, final String name, final Object value) throws Exception {
        final Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    /**
     * Analyze one segment, recording what the side does, and the exception that stops it at the point it is thrown.
     */
    final void analyze(final SegmentObject segment) {
        capture.recordInto(log);
        CheckerWireIndex.clear();
        log.start();
        try {
            beforeAnalyze();
            doAnalyze(segment);
        } catch (final Throwable t) {
            // A NullPointerException's helpful message names a variable, which differs between the two sides.
            log.eventAt("exception " + t.getClass().getName()
                + (t instanceof NullPointerException ? "" : ": " + CheckerObjectRenderer.render(t.getMessage())),
                t.getStackTrace());
        } finally {
            afterAnalyze(segment);
            log.stop();
            CheckerWireIndex.clear();
            capture.recordInto(null);
        }
    }

    /**
     * Prepare telling the span-loop lines their spans, before the segment runs.
     */
    protected abstract void beforeAnalyze();

    /**
     * Tell the span-loop lines still waiting their spans, after the segment ran, failed or not.
     */
    protected abstract void afterAnalyze(SegmentObject segment);

    protected abstract void doAnalyze(SegmentObject segment) throws Exception;

    /**
     * Analyze one segment recording nothing, for the benchmark; a failure is the segment's own and is dropped.
     */
    final void analyzeQuietly(final SegmentObject segment) {
        try {
            doAnalyze(segment);
        } catch (final Throwable ignored) {
            // the benchmark measures the work, failures included, not their outcome
        }
    }

    /**
     * The dynamic updates the profile sends halfway through a pass, as the configuration module would deliver them.
     */
    final void applyMidRunUpdates() {
        if (profile.getMidRunSamplingPolicy() != null) {
            analyzerConfig.getTraceSamplingPolicyWatcher().notify(new ConfigChangeWatcher.ConfigChangeEvent(
                profile.getMidRunSamplingPolicy(), ConfigChangeWatcher.EventType.MODIFY));
        }
        if (profile.getMidRunSearchableTags() != null) {
            searchable.notify(new ConfigChangeWatcher.ConfigChangeEvent(
                profile.getMidRunSearchableTags(), ConfigChangeWatcher.EventType.MODIFY));
        }
    }

    /**
     * Analyze one segment recording nothing but the alias setups it sends, for the rows a warm cache loads.
     */
    final void analyzeForAliasSetups(final SegmentObject segment) {
        collectingAliasSetups = true;
        try {
            analyzeQuietly(segment);
        } finally {
            collectingAliasSetups = false;
        }
    }

    /**
     * Hand every source the side sends to the sink as well, for the benchmark to consume.
     *
     * @param sink what takes each source after the side looked at it
     */
    final void sendSourcesTo(final Consumer<ISource> sink) {
        this.sink = sink;
    }

    /**
     * @return every alias setup this side sent while recording or collecting them, in order
     */
    final List<NetworkAddressAliasSetup> aliasSetups() {
        return new ArrayList<>(aliasSetups);
    }

    /**
     * Put rows into this side's alias cache, as the cache reload does.
     */
    final void loadAliasRows(final List<NetworkAddressAlias> rows) {
        CheckerAliasCacheLoader.load(aliasCache, rows);
    }

    CheckerEventLog log() {
        return log;
    }

    /**
     * @return the endpoint grouping's per-service state, compared after each segment
     */
    String groupingState() throws Exception {
        final EndpointNameGrouping grouping = naming.grouping();
        final Field cached = EndpointNameGrouping.class.getDeclaredField("cachedHttpUris");
        cached.setAccessible(true);
        final Field unformatted = EndpointNameGrouping.class.getDeclaredField("unformattedHttpUrisCache");
        unformatted.setAccessible(true);
        final Cache<?, ?> cache = (Cache<?, ?>) unformatted.get(grouping);
        return CheckerObjectRenderer.render(cached.get(grouping)) + " / " + CheckerObjectRenderer.render(cache.asMap());
    }
}
