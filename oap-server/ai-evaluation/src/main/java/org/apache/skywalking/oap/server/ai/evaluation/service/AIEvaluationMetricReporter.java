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

package org.apache.skywalking.oap.server.ai.evaluation.service;

import com.google.common.collect.ImmutableMap;

import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.meter.analyzer.v2.MetricConvert;
import org.apache.skywalking.oap.meter.analyzer.v2.dsl.Sample;
import org.apache.skywalking.oap.meter.analyzer.v2.dsl.SampleFamily;
import org.apache.skywalking.oap.meter.analyzer.v2.dsl.SampleFamilyBuilder;
import org.apache.skywalking.oap.server.ai.evaluation.context.AIEvaluationContext;
import org.apache.skywalking.oap.server.ai.evaluation.plan.EvaluationResult;
import org.apache.skywalking.oap.server.core.config.NamingControl;

@Slf4j
public class AIEvaluationMetricReporter {
    public static final String RULE_CATALOG = "gen-ai-evaluation-rules";
    public static final String RULE_NAME = "gen-ai-model";
    public static final String SAMPLE_SCORE_NAME = "gen_ai_model_evaluation_score_ppm";
    private static final double SCORE_SCALE = 1_000_000D;

    private final List<MetricConvert> metricConverts;
    private final NamingControl namingControl;

    public AIEvaluationMetricReporter(final List<MetricConvert> metricConverts,
                                      final NamingControl namingControl) {
        this.metricConverts = metricConverts;
        this.namingControl = namingControl;
    }

    public void reportScore(final AIEvaluationContext context,
                            final EvaluationResult result,
                            final long sampleTime) {
        final double score;
        try {
            score = Double.parseDouble(result.getValue());
        } catch (NumberFormatException e) {
            log.warn("Skip AI evaluation score metric, invalid score: {}", result.getValue(), e);
            return;
        }

        final Sample sample = Sample.builder()
                .name(SAMPLE_SCORE_NAME)
                .timestamp(sampleTime)
                .value(score * SCORE_SCALE)
                .labels(ImmutableMap.copyOf(labels(context, result, namingControl)))
                .build();
        final ImmutableMap<String, SampleFamily> sampleFamilies = ImmutableMap.of(
                SAMPLE_SCORE_NAME,
                SampleFamilyBuilder.newBuilder(sample).build()
        );
        metricConverts.forEach(convert -> convert.toMeter(sampleFamilies));
    }

    private static Map<String, String> labels(final AIEvaluationContext context,
                                              final EvaluationResult result,
                                              final NamingControl namingControl) {
        return ImmutableMap.of(
                "provider_name", defaultString(namingControl.formatServiceName(context.getProviderName())),
                "model_name", defaultString(namingControl.formatInstanceName(context.getModelName())),
                "task_name", defaultString(result.getName())
        );
    }

    private static String defaultString(final String value) {
        return value == null ? "" : value;
    }
}
