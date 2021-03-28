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

package org.apache.skywalking.oap.meter.analyzer;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import io.vavr.control.Try;
import java.util.List;
import java.util.StringJoiner;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.meter.analyzer.dsl.SampleFamily;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterSystem;
import org.elasticsearch.common.Strings;

import static java.util.stream.Collectors.toList;

/**
 * MetricConvert converts {@link SampleFamily} collection to meter-system metrics, then store them to backend storage.
 */
@Slf4j
public class MetricConvert {

    public static <T> Stream<T> log(Try<T> t, String debugMessage) {
        return t
            .onSuccess(i -> log.debug(debugMessage + " :{}", i))
            .onFailure(e -> log.debug(debugMessage + " failed", e))
            .toJavaStream();
    }

    private final List<Analyzer> analyzers;

    public MetricConvert(MetricRuleConfig rule, MeterSystem service) {
        Preconditions.checkState(!Strings.isNullOrEmpty(rule.getMetricPrefix()));
        this.analyzers = rule.getMetricsRules().stream().map(
            r -> Analyzer.build(
                formatMetricName(rule, r.getName()),
                Strings.isEmpty(rule.getExpSuffix()) ?
                    r.getExp() : String.format("(%s).%s", r.getExp(), rule.getExpSuffix()),
                service
            )
        ).collect(toList());
    }

    /**
     * toMeter transforms {@link SampleFamily} collection  to meter-system metrics.
     *
     * @param sampleFamilies {@link SampleFamily} collection.
     */
    public void toMeter(final ImmutableMap<String, SampleFamily> sampleFamilies) {
        Preconditions.checkNotNull(sampleFamilies);
        if (sampleFamilies.size() < 1) {
            return;
        }
        for (Analyzer each : analyzers) {
            try {
                each.analyse(sampleFamilies);
            } catch (Throwable t) {
                log.error("Analyze {} error", each, t);
            }
        }
    }

    private String formatMetricName(MetricRuleConfig rule, String meterRuleName) {
        StringJoiner metricName = new StringJoiner("_");
        metricName.add(rule.getMetricPrefix()).add(meterRuleName);
        return metricName.toString();
    }
}
