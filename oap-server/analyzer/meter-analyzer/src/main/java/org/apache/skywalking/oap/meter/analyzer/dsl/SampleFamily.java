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

package org.apache.skywalking.oap.meter.analyzer.dsl;

import com.google.common.base.CharMatcher;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.AtomicDouble;
import groovy.lang.Closure;
import io.vavr.Function2;
import io.vavr.Function3;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.apache.skywalking.oap.meter.analyzer.dsl.EntityDescription.EndpointEntityDescription;
import org.apache.skywalking.oap.meter.analyzer.dsl.EntityDescription.EntityDescription;
import org.apache.skywalking.oap.meter.analyzer.dsl.EntityDescription.InstanceEntityDescription;
import org.apache.skywalking.oap.meter.analyzer.dsl.EntityDescription.ServiceEntityDescription;
import org.apache.skywalking.oap.meter.analyzer.dsl.tagOpt.K8sRetagType;
import org.apache.skywalking.oap.server.core.UnexpectedException;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterEntity;
import org.apache.skywalking.oap.server.core.analysis.meter.ScopeType;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.DoubleBinaryOperator;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static java.util.function.UnaryOperator.identity;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;

/**
 * SampleFamily represents a collection of {@link Sample}.
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode
@ToString
public class SampleFamily {
    public static final SampleFamily EMPTY = new SampleFamily(new Sample[0], RunningContext.EMPTY);

    static SampleFamily build(RunningContext ctx, Sample... samples) {
        Preconditions.checkNotNull(samples);
        Preconditions.checkArgument(samples.length > 0);
        return new SampleFamily(samples, Optional.ofNullable(ctx).orElseGet(RunningContext::instance));
    }

    public final Sample[] samples;

    public final RunningContext context;

    /**
     * Following operations are used in DSL
     */

    /* tag filter operations*/
    public SampleFamily tagEqual(String... labels) {
        return match(labels, InternalOps::stringComp);
    }

    public SampleFamily tagNotEqual(String[] labels) {
        return match(labels, (sv, lv) -> !InternalOps.stringComp(sv, lv));
    }

    public SampleFamily tagMatch(String[] labels) {
        return match(labels, String::matches);
    }

    public SampleFamily tagNotMatch(String[] labels) {
        return match(labels, (sv, lv) -> !sv.matches(lv));
    }

    /* value filter operations*/
    public SampleFamily valueEqual(double compValue) {
        return valueMatch(CompType.EQUAL, compValue, InternalOps::doubleComp);
    }

    public SampleFamily valueNotEqual(double compValue) {
        return valueMatch(CompType.NOT_EQUAL, compValue, InternalOps::doubleComp);
    }

    public SampleFamily valueGreater(double compValue) {
        return valueMatch(CompType.GREATER, compValue, InternalOps::doubleComp);
    }

    public SampleFamily valueGreaterEqual(double compValue) {
        return valueMatch(CompType.GREATER_EQUAL, compValue, InternalOps::doubleComp);
    }

    public SampleFamily valueLess(double compValue) {
        return valueMatch(CompType.LESS, compValue, InternalOps::doubleComp);
    }

    public SampleFamily valueLessEqual(double compValue) {
        return valueMatch(CompType.LESS_EQUAL, compValue, InternalOps::doubleComp);
    }

    /* Binary operator overloading*/
    public SampleFamily plus(Number number) {
        return newValue(v -> v + number.doubleValue());
    }

    public SampleFamily minus(Number number) {
        return newValue(v -> v - number.doubleValue());
    }

    public SampleFamily multiply(Number number) {
        return newValue(v -> v * number.doubleValue());
    }

    public SampleFamily div(Number number) {
        return newValue(v -> v / number.doubleValue());
    }

    public SampleFamily negative() {
        return newValue(v -> -v);
    }

    public SampleFamily plus(SampleFamily another) {
        if (this == EMPTY && another == EMPTY) {
            return SampleFamily.EMPTY;
        }
        if (this == EMPTY) {
            return another;
        }
        if (another == EMPTY) {
            return this;
        }
        return newValue(another, Double::sum);
    }

    public SampleFamily minus(SampleFamily another) {
        if (this == EMPTY && another == EMPTY) {
            return SampleFamily.EMPTY;
        }
        if (this == EMPTY) {
            return another.negative();
        }
        if (another == EMPTY) {
            return this;
        }
        return newValue(another, (a, b) -> a - b);
    }

    public SampleFamily multiply(SampleFamily another) {
        if (this == EMPTY || another == EMPTY) {
            return SampleFamily.EMPTY;
        }
        return newValue(another, (a, b) -> a * b);
    }

    public SampleFamily div(SampleFamily another) {
        if (this == EMPTY) {
            return SampleFamily.EMPTY;
        }
        if (another == EMPTY) {
            return div(0.0);
        }
        return newValue(another, (a, b) -> a / b);
    }

    /* Aggregation operators */
    public SampleFamily sum(List<String> by) {
        return aggregate(by, Double::sum);
    }

    public SampleFamily max(List<String> by) {
        return aggregate(by, Double::max);
    }

    public SampleFamily min(List<String> by) {
        return aggregate(by, Double::min);
    }

    public SampleFamily avg(List<String> by) {
        ExpressionParsingContext.get().ifPresent(ctx -> ctx.aggregationLabels.addAll(by));
        if (this == EMPTY) {
            return EMPTY;
        }
        if (by == null) {
            double result = Arrays.stream(samples).mapToDouble(Sample::getValue).average().orElse(0.0D);
            return SampleFamily.build(this.context, InternalOps.newSample(samples[0].name, ImmutableMap.of(), samples[0].timestamp, result));
        }

        return SampleFamily.build(
            this.context,
            Arrays.stream(samples)
                  .collect(groupingBy(it -> InternalOps.getLabels(by, it), mapping(identity(), toList())))
                  .entrySet().stream()
                  .map(entry -> InternalOps.newSample(
                      entry.getValue().get(0).getName(),
                      entry.getKey(),
                      entry.getValue().get(0).getTimestamp(),
                      entry.getValue().stream().mapToDouble(Sample::getValue).average().orElse(0.0D)
                  ))
                  .toArray(Sample[]::new)
        );
    }

    protected SampleFamily aggregate(List<String> by, DoubleBinaryOperator aggregator) {
        ExpressionParsingContext.get().ifPresent(ctx -> ctx.aggregationLabels.addAll(by));
        if (this == EMPTY) {
            return EMPTY;
        }
        if (by == null) {
            double result = Arrays.stream(samples).mapToDouble(s -> s.value).reduce(aggregator).orElse(0.0D);
            return SampleFamily.build(this.context, InternalOps.newSample(samples[0].name, ImmutableMap.of(), samples[0].timestamp, result));
        }
        return SampleFamily.build(
            this.context,
            Arrays.stream(samples)
                  .collect(groupingBy(it -> InternalOps.getLabels(by, it), mapping(identity(), toList())))
                  .entrySet().stream()
                  .map(entry -> InternalOps.newSample(
                      entry.getValue().get(0).getName(),
                      entry.getKey(),
                      entry.getValue().get(0).getTimestamp(),
                      entry.getValue().stream().mapToDouble(Sample::getValue).reduce(aggregator).orElse(0.0D)
                  ))
                  .toArray(Sample[]::new)
        );
    }

    /* Function */
    public SampleFamily increase(String range) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(range));
        if (this == EMPTY) {
            return EMPTY;
        }
        return SampleFamily.build(
            this.context,
            Arrays.stream(samples)
                  .map(sample -> sample.increase(range, (lowerBoundValue, unused) -> sample.value - lowerBoundValue))
                  .toArray(Sample[]::new)
        );
    }

    public SampleFamily rate(String range) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(range));
        if (this == EMPTY) {
            return EMPTY;
        }
        return SampleFamily.build(
            this.context,
            Arrays.stream(samples)
                  .map(sample -> sample.increase(
                      range,
                      (lowerBoundValue, lowerBoundTime) -> {
                          final long timeDiff = (sample.timestamp - lowerBoundTime) / 1000;
                          return timeDiff < 1L ? 0.0 : (sample.value - lowerBoundValue) / timeDiff;
                      }
                  ))
                  .toArray(Sample[]::new)
        );
    }

    public SampleFamily irate() {
        if (this == EMPTY) {
            return EMPTY;
        }
        return SampleFamily.build(
            this.context,
            Arrays.stream(samples)
                  .map(sample -> sample.increase(
                      (lowerBoundValue, lowerBoundTime) -> {
                          final long timeDiff = (sample.timestamp - lowerBoundTime) / 1000;
                          return timeDiff < 1L ? 0.0 : (sample.value - lowerBoundValue) / timeDiff;
                      }
                  ))
                  .toArray(Sample[]::new)
        );
    }

    @SuppressWarnings(value = "unchecked")
    public SampleFamily tag(Closure<?> cl) {
        if (this == EMPTY) {
            return EMPTY;
        }
        return SampleFamily.build(
            this.context,
            Arrays.stream(samples)
                  .map(sample -> {
                      Object delegate = new Object();
                      Closure<?> c = cl.rehydrate(delegate, sample, delegate);
                      Map<String, String> arg = Maps.newHashMap(sample.labels);
                      Object r = c.call(arg);
                      return sample.toBuilder()
                                   .labels(
                                       ImmutableMap.copyOf(Optional.ofNullable((r instanceof Map) ? (Map<String, String>) r : null)
                                                                   .orElse(arg)))
                                   .build();
                  }).toArray(Sample[]::new)
        );
    }

    /* k8s retags*/
    public SampleFamily retagByK8sMeta(String newLabelName, K8sRetagType type, String existingLabelName, String namespaceLabelName) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(newLabelName));
        Preconditions.checkArgument(!Strings.isNullOrEmpty(existingLabelName));
        Preconditions.checkArgument(!Strings.isNullOrEmpty(namespaceLabelName));
        ExpressionParsingContext.get().ifPresent(ctx -> ctx.isRetagByK8sMeta = true);
        if (this == EMPTY) {
            return EMPTY;
        }

        return SampleFamily.build(this.context, type.execute(samples, newLabelName, existingLabelName, namespaceLabelName));
    }

    public SampleFamily histogram() {
        return histogram("le", this.context.defaultHistogramBucketUnit);
    }

    public SampleFamily histogram(String le) {
        return histogram(le, this.context.defaultHistogramBucketUnit);
    }

    public SampleFamily histogram(String le, TimeUnit unit) {
        long scale = unit.toMillis(1);
        Preconditions.checkArgument(scale > 0);
        ExpressionParsingContext.get().ifPresent(ctx -> ctx.isHistogram = true);
        if (this == EMPTY) {
            return EMPTY;
        }
        AtomicDouble pre = new AtomicDouble();
        AtomicReference<String> preLe = new AtomicReference<>("0");
        return SampleFamily.build(
            this.context,
            Stream.concat(
                Arrays.stream(samples).filter(s -> !s.labels.containsKey(le)),
                Arrays.stream(samples)
                      .filter(s -> s.labels.containsKey(le))
                      .sorted(Comparator.comparingDouble(s -> Double.parseDouble(s.labels.get(le))))
                      .map(s -> {
                          double r = this.context.histogramType == HistogramType.ORDINARY ? s.value : s.value - pre.get();
                          pre.set(s.value);
                          ImmutableMap<String, String> ll = ImmutableMap.<String, String>builder()
                              .putAll(Maps.filterKeys(s.labels, key -> !Objects.equals(key, le)))
                              .put("le", String.valueOf((long) ((Double.parseDouble(this.context.histogramType == HistogramType.ORDINARY ? s.labels.get(le) : preLe.get())) * scale))).build();
                          preLe.set(s.labels.get(le));
                          return InternalOps.newSample(s.name, ll, s.timestamp, r);
                      })
            ).toArray(Sample[]::new)
        );
    }

    public SampleFamily histogram_percentile(List<Integer> percentiles) {
        Preconditions.checkArgument(percentiles.size() > 0);
        int[] p = percentiles.stream().mapToInt(i -> i).toArray();
        ExpressionParsingContext.get().ifPresent(ctx -> {
            Preconditions.checkState(ctx.isHistogram, "histogram() should be invoked before invoking histogram_percentile()");
            ctx.percentiles = p;
        });
        return this;
    }

    public SampleFamily service(List<String> labelKeys) {
        Preconditions.checkArgument(labelKeys.size() > 0);
        ExpressionParsingContext.get().ifPresent(ctx -> {
            ctx.scopeType = ScopeType.SERVICE;
            ctx.scopeLabels.addAll(labelKeys);
        });
        if (this == EMPTY) {
            return EMPTY;
        }
        return createMeterSamples(new ServiceEntityDescription(labelKeys));
    }

    public SampleFamily instance(List<String> serviceKeys, List<String> instanceKeys) {
        Preconditions.checkArgument(serviceKeys.size() > 0);
        Preconditions.checkArgument(instanceKeys.size() > 0);
        ExpressionParsingContext.get().ifPresent(ctx -> ctx.scopeType = ScopeType.SERVICE_INSTANCE);
        ExpressionParsingContext.get().ifPresent(ctx -> {
            ctx.scopeType = ScopeType.SERVICE_INSTANCE;
            ctx.scopeLabels.addAll(serviceKeys);
            ctx.scopeLabels.addAll(instanceKeys);
        });
        if (this == EMPTY) {
            return EMPTY;
        }
        return createMeterSamples(new InstanceEntityDescription(serviceKeys, instanceKeys));
    }

    public SampleFamily endpoint(List<String> serviceKeys, List<String> endpointKeys) {
        Preconditions.checkArgument(serviceKeys.size() > 0);
        Preconditions.checkArgument(endpointKeys.size() > 0);
        ExpressionParsingContext.get().ifPresent(ctx -> ctx.scopeType = ScopeType.ENDPOINT);
        ExpressionParsingContext.get().ifPresent(ctx -> {
            ctx.scopeType = ScopeType.ENDPOINT;
            ctx.scopeLabels.addAll(serviceKeys);
            ctx.scopeLabels.addAll(endpointKeys);
        });
        if (this == EMPTY) {
            return EMPTY;
        }
        return createMeterSamples(new EndpointEntityDescription(serviceKeys, endpointKeys));
    }

    private SampleFamily createMeterSamples(EntityDescription entityDescription) {
        Map<MeterEntity, Sample[]> meterSamples = new HashMap<>();
        Arrays.stream(samples)
              .collect(groupingBy(it -> InternalOps.getLabels(entityDescription.getLabelKeys(), it), mapping(identity(), toList())))
              .forEach((labels, samples) -> {
                  MeterEntity meterEntity = InternalOps.buildMeterEntity(samples, entityDescription);
                  meterSamples.put(meterEntity, InternalOps.left(samples, entityDescription.getLabelKeys()));
              });

        this.context.setMeterSamples(meterSamples);
        //This samples is original, The grouped samples is in context which mapping with MeterEntity
        return SampleFamily.build(this.context, samples);
    }

    private SampleFamily match(String[] labels, Function2<String, String, Boolean> op) {
        Preconditions.checkArgument(labels.length % 2 == 0);
        Map<String, String> ll = new HashMap<>(labels.length / 2);
        for (int i = 0; i < labels.length; i += 2) {
            ll.put(labels[i], labels[i + 1]);
        }
        Sample[] ss = Arrays.stream(samples)
                            .filter(sample -> ll.entrySet()
                                                .stream()
                                                .allMatch(entry -> op.apply(sample.labels.getOrDefault(entry.getKey(), ""), entry.getValue())))
                            .toArray(Sample[]::new);
        return ss.length > 0 ? SampleFamily.build(this.context, ss) : EMPTY;
    }

    private SampleFamily valueMatch(CompType compType,
                                    double compValue,
                                    Function3<CompType, Double, Double, Boolean> op) {
        Sample[] ss = Arrays.stream(samples)
                            .filter(sample -> op.apply(compType, sample.value, compValue)).toArray(Sample[]::new);
        return ss.length > 0 ? SampleFamily.build(this.context, ss) : EMPTY;
    }

    SampleFamily newValue(Function<Double, Double> transform) {
        if (this == EMPTY) {
            return EMPTY;
        }
        Sample[] ss = new Sample[samples.length];
        for (int i = 0; i < ss.length; i++) {
            ss[i] = samples[i].newValue(transform);
        }
        return SampleFamily.build(this.context, ss);
    }

    private SampleFamily newValue(SampleFamily another, Function2<Double, Double, Double> transform) {
        Sample[] ss = Arrays.stream(samples)
                            .flatMap(cs -> io.vavr.collection.Stream.of(another.samples)
                                                                    .find(as -> cs.labels.equals(as.labels))
                                                                    .map(as -> cs.toBuilder().value(transform.apply(cs.value, as.value)))
                                                                    .map(Sample.SampleBuilder::build)
                                                                    .toJavaStream())
                            .toArray(Sample[]::new);
        return ss.length > 0 ? SampleFamily.build(this.context, ss) : EMPTY;
    }

    public SampleFamily downsampling(final DownsamplingType type) {
        ExpressionParsingContext.get().ifPresent(it -> it.downsampling = type);
        return this;
    }

    /**
     * The parsing context holds key results more than sample collection.
     */
    @ToString
    @EqualsAndHashCode
    @Getter
    @Setter
    @Builder
    public static class RunningContext {

        static RunningContext EMPTY = instance();

        static RunningContext instance() {
            return RunningContext.builder()
                .histogramType(HistogramType.CUMULATIVE)
                .defaultHistogramBucketUnit(TimeUnit.SECONDS)
                .build();
        }

        private Map<MeterEntity, Sample[]> meterSamples = new HashMap<>();

        private HistogramType histogramType;

        private TimeUnit defaultHistogramBucketUnit;
    }

    private static class InternalOps {

        private static Sample[] left(List<Sample> samples, List<String> labelKeys) {
            return samples.stream().map(s -> {
                ImmutableMap<String, String> ll = ImmutableMap.<String, String>builder()
                    .putAll(Maps.filterKeys(s.labels, key -> !labelKeys.contains(key)))
                    .build();
                return s.toBuilder().labels(ll).build();
            }).toArray(Sample[]::new);
        }

        private static String dim(List<Sample> samples, List<String> labelKeys) {
            String name = labelKeys.stream()
                                   .map(k -> samples.get(0).labels.getOrDefault(k, ""))
                                   .collect(Collectors.joining("."));
            return CharMatcher.is('.').trimFrom(name);
        }

        private static MeterEntity buildMeterEntity(List<Sample> samples,
                                                    EntityDescription entityDescription) {
            switch (entityDescription.getScopeType()) {
                case SERVICE:
                    return MeterEntity.newService(InternalOps.dim(samples, entityDescription.getServiceKeys()));
                case SERVICE_INSTANCE:
                    return MeterEntity.newServiceInstance(
                        InternalOps.dim(samples, entityDescription.getServiceKeys()),
                        InternalOps.dim(samples, entityDescription.getInstanceKeys())
                    );
                case ENDPOINT:
                    return MeterEntity.newEndpoint(
                        InternalOps.dim(samples, entityDescription.getServiceKeys()),
                        InternalOps.dim(samples, entityDescription.getEndpointKeys())
                    );
                default: throw new UnexpectedException("Unexpected scope type of entityDescription " + entityDescription.toString());
            }
        }

        private static Sample newSample(String name, ImmutableMap<String, String> labels, long timestamp, double newValue) {
            return Sample.builder()
                         .value(newValue)
                         .labels(labels)
                         .timestamp(timestamp)
                         .name(name)
                         .build();
        }

        private static boolean stringComp(String a, String b) {
            if (Strings.isNullOrEmpty(a) && Strings.isNullOrEmpty(b)) {
                return true;
            }
            if (Strings.isNullOrEmpty(a)) {
                return false;
            }
            return a.equals(b);
        }

        private static boolean doubleComp(CompType compType, double a, double b) {
            int result = Double.compare(a, b);
            switch (compType) {
                case EQUAL:
                    return result == 0;
                case NOT_EQUAL:
                    return result != 0;
                case GREATER:
                    return result == 1;
                case GREATER_EQUAL:
                    return result == 0 || result == 1;
                case LESS:
                    return result == -1;
                case LESS_EQUAL:
                    return result == 0 || result == -1;
            }

            return false;
        }

        private static ImmutableMap<String, String> getLabels(final List<String> labelKeys, final Sample sample) {
            return labelKeys.stream()
                            .collect(toImmutableMap(
                                Function.identity(),
                                labelKey -> sample.labels.getOrDefault(labelKey, "")
                            ));
        }
    }

    private enum CompType {
        EQUAL, NOT_EQUAL, LESS, LESS_EQUAL, GREATER, GREATER_EQUAL
    }
}
