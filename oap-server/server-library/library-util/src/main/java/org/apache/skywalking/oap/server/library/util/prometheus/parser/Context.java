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

package org.apache.skywalking.oap.server.library.util.prometheus.parser;

import com.google.common.collect.Maps;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.skywalking.oap.server.library.util.prometheus.metrics.Counter;
import org.apache.skywalking.oap.server.library.util.prometheus.metrics.Gauge;
import org.apache.skywalking.oap.server.library.util.prometheus.metrics.Histogram;
import org.apache.skywalking.oap.server.library.util.prometheus.metrics.MetricFamily;
import org.apache.skywalking.oap.server.library.util.prometheus.metrics.MetricType;
import org.apache.skywalking.oap.server.library.util.prometheus.metrics.Summary;
import org.apache.skywalking.oap.server.library.util.prometheus.parser.sample.TextSample;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;

@RequiredArgsConstructor
public class Context {
    private static final Logger LOG = LoggerFactory.getLogger(Context.class);
    public MetricFamily metricFamily;

    public String name = "";
    public String help = "";
    public MetricType type = null;
    public List<String> allowedNames = new ArrayList<>();
    public List<TextSample> samples = new ArrayList<>();

    private final long now;

    void addAllowedNames(String type) {
        this.type = MetricType.valueOf(type.toUpperCase());
        allowedNames.clear();
        switch (this.type) {
            case COUNTER:
            case GAUGE:
                allowedNames.add(name);
                break;
            case SUMMARY:
                allowedNames.add(name + "_count");
                allowedNames.add(name + "_sum");
                allowedNames.add(name);
                break;
            case HISTOGRAM:
                allowedNames.add(name + "_count");
                allowedNames.add(name + "_sum");
                allowedNames.add(name + "_bucket");
                break;
        }
    }

    void clear() {
        name = "";
        help = "";
        type = null;
        allowedNames.clear();
        samples.clear();
    }

    void end() {
        if (metricFamily != null) {
            return;
        }

        MetricFamily.Builder metricFamilyBuilder = new MetricFamily.Builder();
        metricFamilyBuilder.setName(name);
        metricFamilyBuilder.setHelp(help);
        metricFamilyBuilder.setType(type);

        if (samples.size() < 1) {
            return;
        }
        switch (type) {
            case GAUGE:
                samples.forEach(textSample -> metricFamilyBuilder
                    .addMetric(Gauge.builder()
                        .name(name)
                        .value(convertStringToDouble(textSample.getValue()))
                        .labels(textSample.getLabels())
                        .timestamp(now)
                        .build()));
                break;
            case COUNTER:
                samples.forEach(textSample -> metricFamilyBuilder
                    .addMetric(Counter.builder()
                        .name(name)
                        .value(convertStringToDouble(textSample.getValue()))
                        .labels(textSample.getLabels())
                        .timestamp(now)
                        .build()));
                break;
            case HISTOGRAM:
                samples.stream()
                    .map(sample -> {
                        Map<String, String> labels = Maps.newHashMap(sample.getLabels());
                        labels.remove("le");
                        return Pair.of(labels, sample);
                    })
                    .collect(groupingBy(Pair::getLeft, mapping(Pair::getRight, toList())))
                    .forEach((labels, samples) -> {
                        Histogram.HistogramBuilder hBuilder = Histogram.builder();
                        hBuilder.name(name).timestamp(now);
                        hBuilder.labels(labels);
                        samples.forEach(textSample -> {
                            if (textSample.getName().endsWith("_count")) {
                                hBuilder.sampleCount((long) convertStringToDouble(textSample.getValue()));
                            } else if (textSample.getName().endsWith("_sum")) {
                                hBuilder.sampleSum(convertStringToDouble(textSample.getValue()));
                            } else if (textSample.getLabels().containsKey("le")) {
                                hBuilder.bucket(
                                    convertStringToDouble(textSample.getLabels().remove("le")),
                                    (long) convertStringToDouble(textSample.getValue())
                                );
                            }
                        });
                        metricFamilyBuilder.addMetric(hBuilder.build());
                    });
                break;
            case SUMMARY:
                samples.stream()
                    .map(sample -> {
                        Map<String, String> labels = Maps.newHashMap(sample.getLabels());
                        labels.remove("quantile");
                        return Pair.of(labels, sample);
                    })
                    .collect(groupingBy(Pair::getLeft, mapping(Pair::getRight, toList())))
                    .forEach((labels, samples) -> {
                        Summary.SummaryBuilder sBuilder = Summary.builder();
                        sBuilder.name(name).timestamp(now);
                        sBuilder.labels(labels);
                        samples.forEach(textSample -> {
                            if (textSample.getName().endsWith("_count")) {
                                sBuilder.sampleCount((long) convertStringToDouble(textSample.getValue()));
                            } else if (textSample.getName().endsWith("_sum")) {
                                sBuilder.sampleSum(convertStringToDouble(textSample.getValue()));
                            } else if (textSample.getLabels().containsKey("quantile")) {
                                sBuilder.quantile(
                                    convertStringToDouble(textSample.getLabels().remove("quantile")),
                                    convertStringToDouble(textSample.getValue())
                                );
                            }
                        });
                        metricFamilyBuilder.addMetric(sBuilder.build());
                    });

                break;
        }
        metricFamily = metricFamilyBuilder.build();
    }

    private static double convertStringToDouble(String valueString) {
        double doubleValue;
        if (valueString.equalsIgnoreCase("NaN")) {
            doubleValue = Double.NaN;
        } else if (valueString.equalsIgnoreCase("+Inf")) {
            doubleValue = Double.POSITIVE_INFINITY;
        } else if (valueString.equalsIgnoreCase("-Inf")) {
            doubleValue = Double.NEGATIVE_INFINITY;
        } else {
            doubleValue = Double.parseDouble(valueString);
        }
        return doubleValue;
    }
}
