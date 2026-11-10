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

package org.apache.skywalking.oap.trace.analyzer.v2.compiler.rt;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.meter.EntityTrafficGenerator;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterEntity;
import org.apache.skywalking.oap.server.core.analysis.meter.ScopeType;
import org.apache.skywalking.oap.server.core.analysis.meter.function.AcceptableValue;
import org.apache.skywalking.oap.server.core.analysis.meter.function.BucketedValues;
import org.apache.skywalking.oap.server.core.analysis.metrics.DataLabel;
import org.apache.skywalking.oap.server.core.analysis.metrics.DataTable;
import org.apache.skywalking.oap.server.core.source.DetectPoint;

/**
 * What a generated {@code metrics {}} block calls: the entity's layers, its traffic, the observation's shape, the
 * send, and the report of a block that failed. Not a function a rule calls.
 */
@Slf4j
public final class TalMetricsRuntime {
    /**
     * Layer names already warned about, so an unknown name in a rule costs one log line rather than one per span.
     * Bounded by the names rules spell, which is a handful; the cap only guards a rule that computes names.
     */
    private static final Set<String> REPORTED_UNKNOWN_LAYERS = ConcurrentHashMap.newKeySet();

    private static final int REPORTED_CAP = 1000;

    /**
     * Metric names already warned about, as {@link #REPORTED_UNKNOWN_LAYERS} is for layers.
     */
    private static final Set<String> REPORTED_FAILED_METRICS = ConcurrentHashMap.newKeySet();

    private TalMetricsRuntime() {
    }

    /**
     * @param name a layer name, from a literal or an expression
     * @return the layer; null when the name is empty or not registered, which {@link #layerResolved} turns into
     * skipping the metrics block. An empty name, from a layer expression that gave none, drops the output rather than
     * filing it under a layer nobody chose. {@code Layer.nameOf} is not used because it answers
     * {@code UNDEFINED} for an unknown name, and {@code UNDEFINED} is a layer a rule names on purpose, for the
     * {@code User} end of a relation.
     */
    public static Layer layer(final String name) {
        if (name == null || name.isEmpty()) {
            if (REPORTED_UNKNOWN_LAYERS.size() < REPORTED_CAP && REPORTED_UNKNOWN_LAYERS.add("")) {
                log.warn("A TAL rule built an entity with no layer; its metrics blocks are skipped");
            }
            return null;
        }
        try {
            return Layer.valueOf(name);
        } catch (final IllegalArgumentException e) {
            if (REPORTED_UNKNOWN_LAYERS.size() < REPORTED_CAP && REPORTED_UNKNOWN_LAYERS.add(name)) {
                log.warn("TAL rule named an unknown layer '{}'; the metrics blocks on it are skipped", name);
            }
            return null;
        }
    }

    /**
     * @param entity the entity a metrics block is about to feed
     * @return whether every layer the entity was built with resolved, see {@link #layer}; the generated block
     * runs only then
     */
    public static boolean layerResolved(final MeterEntity entity) {
        if (entity.getLayer() == null) {
            return false;
        }
        final boolean relation = entity.getScopeType() == ScopeType.SERVICE_RELATION
            || entity.getScopeType() == ScopeType.SERVICE_INSTANCE_RELATION;
        return !relation || entity.getDestLayer() != null;
    }

    /**
     * A {@code metrics {}} block threw, typically on a value that does not parse as a number; the generated code
     * has skipped that block only. The first failure of each metric is a warning, later ones are debug.
     *
     * @param metricName the metric the block feeds
     * @param e          what it threw
     */
    public static void metricsBlockFailed(final String metricName, final RuntimeException e) {
        if (REPORTED_FAILED_METRICS.size() < REPORTED_CAP && REPORTED_FAILED_METRICS.add(metricName)) {
            log.warn("TAL metrics block {} failed on a span and is skipped for it; later failures are logged at debug",
                metricName, e);
        } else if (log.isDebugEnabled()) {
            log.debug("TAL metrics block {} failed on a span", metricName, e);
        }
    }

    /**
     * Register the entity so it appears in the service lists and the topology, in the minute its metric lands in.
     * Called before every metric is accepted; the rows are written once per distinct entity per execution,
     * however many metrics blocks share the scope.
     *
     * <p>A service or endpoint entity carries its layer's name as {@code attr0}, as {@code ServiceDecorator} and
     * {@code EndpointDecorator} give the agent metrics, so {@code top_n(..., attr0='...')} filters these too.
     *
     * @param h          the run, whose execution registers each entity once
     * @param entity     the entity a metric is about to be accepted for
     * @param timeMillis the time the metric is bucketed by
     */
    public static void traffic(final TalRuntimeHelper h, final MeterEntity entity, final long timeMillis) {
        if ((entity.getScopeType() == ScopeType.SERVICE || entity.getScopeType() == ScopeType.ENDPOINT)
            && entity.getLayer() != null) {
            entity.setAttr0(entity.getLayer().name());
        }
        if (h.ctx().markTrafficked(entity)) {
            EntityTrafficGenerator.generate(entity, timeMillis);
        } else if (entity.getScopeType() == ScopeType.SERVICE_RELATION && entity.getDetectPoint() != null) {
            // generate() also names the side that owns a service relation's metric, see
            // EntityTrafficGenerator.serviceRelationTraffic; a second entity for an already registered relation,
            // built again by an inline serviceRelation(...), still needs that owner, or its metric files under
            // an empty service.
            entity.setServiceName(entity.getDetectPoint() == DetectPoint.SERVER
                ? entity.getDestServiceName() : entity.getSourceServiceName());
        }
    }

    /**
     * Hand a metric value to the meter system in the minute bucket the span's timestamp falls in.
     *
     * @param h     the run, whose rule's meter system takes the value
     * @param value the value already accepted for its entity
     * @param time  milliseconds; the span's end unless the block named another
     */
    public static void send(final TalRuntimeHelper h, final AcceptableValue<?> value, final long time) {
        value.setTimeBucket(TimeBucket.getMinuteTimeBucket(time));
        h.spec().getMeterSystem().doStreamingCalculation(value);
    }

    /**
     * Place one observation in the bucket it falls into: the first bound not below it, or the last bucket when
     * it is above every bound. The generated code always passes {@code Long.MAX_VALUE} as the last bound, so that
     * last bucket is the overflow bucket.
     *
     * @param bounds the ascending bucket bounds, in the unit of the observation
     * @param value  the observation
     * @return the bucketed value, counting 1 in its bucket
     */
    public static BucketedValues bucketed(final long[] bounds, final long value) {
        final long[] counts = new long[bounds.length];
        int index = bounds.length - 1;
        for (int i = 0; i < bounds.length; i++) {
            if (value <= bounds[i]) {
                index = i;
                break;
            }
        }
        counts[index] = 1L;
        return new BucketedValues(bounds, counts);
    }

    /**
     * Build the one-entry table a labeled function accepts. The stored table separates entries with {@code |}
     * and a label set's labels with {@code ,}, so those two characters in a value are replaced by {@code _};
     * kept as they are, they would split the value when the table is read back.
     *
     * @param labels alternating label name and value
     * @param value  the observation
     * @return the table keyed by the label set
     */
    public static DataTable labeled(final String[] labels, final long value) {
        final DataLabel label = new DataLabel();
        for (int i = 0; i + 1 < labels.length; i += 2) {
            label.put(labels[i], labels[i + 1] == null ? "" : labels[i + 1].replace('|', '_').replace(',', '_'));
        }
        final DataTable table = new DataTable();
        table.put(label, value);
        return table;
    }
}
