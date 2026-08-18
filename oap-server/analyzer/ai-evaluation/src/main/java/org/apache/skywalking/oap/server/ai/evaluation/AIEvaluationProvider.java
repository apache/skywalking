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

package org.apache.skywalking.oap.server.ai.evaluation;

import java.util.Collections;
import java.util.List;
import java.util.Properties;
import org.apache.skywalking.oap.server.analyzer.module.AnalyzerModule;
import org.apache.skywalking.oap.server.analyzer.provider.meter.process.IMeterProcessService;
import org.apache.skywalking.oap.server.analyzer.provider.trace.parser.ISegmentParserService;
import org.apache.skywalking.oap.server.analyzer.provider.trace.parser.listener.GenAIEvaluationAnalysisListener;
import org.apache.skywalking.oap.server.ai.evaluation.judge.JudgeModelProvider;
import org.apache.skywalking.oap.server.ai.evaluation.judge.provider.OpenAICompatibleProvider;
import org.apache.skywalking.oap.server.ai.evaluation.level.EvaluationLevelResolver;
import org.apache.skywalking.oap.server.ai.evaluation.plan.EvaluationInputExtractor;
import org.apache.skywalking.oap.server.ai.evaluation.plan.EvaluationPlanner;
import org.apache.skywalking.oap.server.ai.evaluation.plan.EvaluationPromptBuilder;
import org.apache.skywalking.oap.server.ai.evaluation.plan.EvaluationResultParser;
import org.apache.skywalking.oap.server.ai.evaluation.service.AIEvaluationMetricReporter;
import org.apache.skywalking.oap.server.ai.evaluation.service.AIEvaluationService;
import org.apache.skywalking.oap.server.ai.evaluation.service.IAIEvaluationService;
import org.apache.skywalking.oap.server.ai.evaluation.service.sample.DefaultAIEvaluationSamplingPolicy;
import org.apache.skywalking.oap.server.ai.evaluation.service.strategy.AIEvaluationStrategy;
import org.apache.skywalking.oap.server.ai.evaluation.service.strategy.span.SpanAIEvaluationStrategy;
import org.apache.skywalking.oap.server.ai.evaluation.task.EvaluationTaskRegistry;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.library.module.ModuleConfig;
import org.apache.skywalking.oap.server.library.module.ModuleDefine;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.module.ServiceNotProvidedException;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;
import org.apache.skywalking.oap.server.telemetry.api.CounterMetrics;
import org.apache.skywalking.oap.server.telemetry.api.MetricsCreator;
import org.apache.skywalking.oap.server.telemetry.api.MetricsTag;

public class AIEvaluationProvider extends ModuleProvider {
    private static final int MAX_SAMPLE_RATE = 1_000_000;
    private AIEvaluationConfig config = new AIEvaluationConfig();
    private AIEvaluationService aiEvaluationService;
    private AIEvaluationMetricReporter metricReporter;

    @Override
    public String name() {
        return "default";
    }

    @Override
    public Class<? extends ModuleDefine> module() {
        return AIEvaluationModule.class;
    }

    @Override
    public ConfigCreator<? extends ModuleConfig> newConfigCreator() {
        return new ConfigCreator<AIEvaluationConfig>() {
            @Override
            public Class<AIEvaluationConfig> type() {
                return AIEvaluationConfig.class;
            }

            @Override
            public void onInitialized(final AIEvaluationConfig initialized) {
                config = initialized;
            }
        };
    }

    @Override
    public void prepare() throws ServiceNotProvidedException, ModuleStartException {
        final int sampleRate = config.getSampleRate();
        final int bufferSize = config.getBufferSize();
        final int consumerThreads = config.getConsumerThreads();
        final int maxContentLength = config.getMaxContentLength();
        config = new AIEvaluationConfigLoader().load();
        config.setSampleRate(sampleRate);
        config.setBufferSize(bufferSize);
        config.setConsumerThreads(consumerThreads);
        config.setMaxContentLength(maxContentLength);
        validateConfig(config);
        if (config.getSampleRate() < 0 || config.getSampleRate() > MAX_SAMPLE_RATE) {
            throw new IllegalArgumentException(
                "sampleRate: " + config.getSampleRate() + ", should be between 0 and " + MAX_SAMPLE_RATE);
        }
        if (config.getBufferSize() <= 0) {
            throw new IllegalArgumentException("bufferSize should be greater than 0");
        }
        if (config.getConsumerThreads() <= 0) {
            throw new IllegalArgumentException("consumerThreads should be greater than 0");
        }
        if (config.getMaxContentLength() <= 0) {
            throw new IllegalArgumentException("maxContentLength should be greater than 0");
        }
        aiEvaluationService = new AIEvaluationService(
            new DefaultAIEvaluationSamplingPolicy(config.getSampleRate()),
            createJudgeProvider(),
            config.getBufferSize(),
            config.getConsumerThreads()
        );
        registerServiceImplementation(IAIEvaluationService.class, aiEvaluationService);
    }

    @Override
    public void start() throws ServiceNotProvidedException, ModuleStartException {
        metricReporter = createMetricReporter();
        final CounterMetrics incompleteSpanCounter = createDroppedCounter("incomplete_span");
        aiEvaluationService.setDroppedCounters(
            createDroppedCounter("pipeline_capacity"),
            incompleteSpanCounter
        );
        aiEvaluationService.setErrorCounters(
            createErrorCounter("rejected"),
            createErrorCounter("timeout"),
            createErrorCounter("invalid_response")
        );
        aiEvaluationService.setStrategies(createStrategies(incompleteSpanCounter));
        getManager().find(AnalyzerModule.NAME)
                    .provider()
                    .getService(ISegmentParserService.class)
                    .addListenerFactory(new GenAIEvaluationAnalysisListener.Factory(getManager()));
    }

    @Override
    public void notifyAfterCompleted() throws ServiceNotProvidedException, ModuleStartException {
    }

    @Override
    public String[] requiredModules() {
        return new String[] {
            CoreModule.NAME,
            AnalyzerModule.NAME,
            TelemetryModule.NAME
        };
    }

    private CounterMetrics createDroppedCounter(final String reason) {
        final MetricsCreator metricsCreator = getManager().find(TelemetryModule.NAME)
                                                          .provider()
                                                          .getService(MetricsCreator.class);
        return metricsCreator.createCounter(
            "ai_evaluation_dropped_count",
            "The number of AI evaluation tasks dropped before execution.",
            new MetricsTag.Keys("reason"),
            new MetricsTag.Values(reason)
        );
    }

    private CounterMetrics createErrorCounter(final String reason) {
        final MetricsCreator metricsCreator = getManager().find(TelemetryModule.NAME)
                                                          .provider()
                                                          .getService(MetricsCreator.class);
        return metricsCreator.createCounter(
            "ai_evaluation_error_count",
            "The number of AI evaluation failures.",
            new MetricsTag.Keys("reason"),
            new MetricsTag.Values(reason)
        );
    }

    private JudgeModelProvider createJudgeProvider() throws ModuleStartException {
        final Properties judge = config.getJudge();
        final String provider = getString(judge, "provider");
        if ("openai".equalsIgnoreCase(provider)) {
            return new OpenAICompatibleProvider(judge);
        }
        throw new ModuleStartException("Unsupported AI evaluation judge provider: " + provider);
    }

    private List<AIEvaluationStrategy> createStrategies(final CounterMetrics incompleteSpanCounter) {
        final EvaluationTaskRegistry taskRegistry = new EvaluationTaskRegistry(config.getTasks());
        final EvaluationInputExtractor inputExtractor = new EvaluationInputExtractor();
        final NamingControl namingControl = getManager().find(CoreModule.NAME).provider().getService(NamingControl.class);
        return Collections.singletonList(new SpanAIEvaluationStrategy(
            taskRegistry,
            new EvaluationPlanner(inputExtractor),
            new EvaluationPromptBuilder(config.getSystemPrompt(), config.getMaxContentLength()),
            new EvaluationResultParser(),
            metricReporter,
            namingControl,
            new EvaluationLevelResolver(config.getLevel()),
            incompleteSpanCounter
        ));
    }

    private AIEvaluationMetricReporter createMetricReporter() {
        final IMeterProcessService meterProcessService = getManager().find(AnalyzerModule.NAME)
                                                                      .provider()
                                                                      .getService(
                                                                          IMeterProcessService.class);
        final NamingControl namingControl = getManager().find(CoreModule.NAME).provider().getService(NamingControl.class);
        return new AIEvaluationMetricReporter(meterProcessService, namingControl);
    }

    private static void validateConfig(final AIEvaluationConfig config) throws ModuleStartException {
        final Properties judge = config.getJudge();
        if (judge == null || judge.isEmpty()) {
            throw new ModuleStartException("AI evaluation judge config is required.");
        }
        if (StringUtil.isBlank(getString(judge, "provider"))) {
            throw new ModuleStartException("AI evaluation judge config [provider] is required.");
        }
        if (StringUtil.isBlank(config.getSystemPrompt())) {
            throw new ModuleStartException("AI evaluation system-prompt is required.");
        }
    }

    private static String getString(final Properties properties, final String key) {
        if (properties == null) {
            return null;
        }
        final Object value = properties.get(key);
        return value == null ? null : String.valueOf(value);
    }

}
