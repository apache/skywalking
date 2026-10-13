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

package org.apache.skywalking.oap.server.core.analysis.meter;

import com.google.gson.JsonObject;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.manual.endpoint.EndpointTraffic;
import org.apache.skywalking.oap.server.core.analysis.manual.instance.InstanceTraffic;
import org.apache.skywalking.oap.server.core.analysis.manual.relation.process.ProcessRelationClientSideMetrics;
import org.apache.skywalking.oap.server.core.analysis.manual.relation.process.ProcessRelationServerSideMetrics;
import org.apache.skywalking.oap.server.core.analysis.manual.relation.instance.ServiceInstanceRelationClientSideMetrics;
import org.apache.skywalking.oap.server.core.analysis.manual.relation.instance.ServiceInstanceRelationServerSideMetrics;
import org.apache.skywalking.oap.server.core.analysis.manual.relation.service.ServiceRelationClientSideMetrics;
import org.apache.skywalking.oap.server.core.analysis.manual.relation.service.ServiceRelationServerSideMetrics;
import org.apache.skywalking.oap.server.core.analysis.manual.service.ServiceTraffic;
import org.apache.skywalking.oap.server.core.analysis.worker.MetricsStreamProcessor;
import org.apache.skywalking.oap.server.core.source.DetectPoint;
import org.apache.skywalking.oap.server.library.util.StringUtil;

import static java.util.Objects.requireNonNull;

/**
 * Turns a {@link MeterEntity} into the traffic rows the service lists and {@code TopologyQueryService} read:
 * {@link ServiceTraffic}, {@link InstanceTraffic}, {@link EndpointTraffic} and the relation metrics.
 *
 * <p>Every engine that builds a {@code MeterEntity} calls this, so an entity's existence reaches the UI the same
 * way whether MAL or TAL produced it.
 */
public class EntityTrafficGenerator {
    private EntityTrafficGenerator() {
    }

    /**
     * @param entity the entity a metric was just accepted for
     */
    public static void generate(final MeterEntity entity) {
        generate(entity, System.currentTimeMillis());
    }

    /**
     * @param entity     the entity a metric was just accepted for
     * @param timeMillis the time the rows are stamped with, so they land in the minute the metric does
     */
    public static void generate(final MeterEntity entity, final long timeMillis) {
        final long minute = TimeBucket.getMinuteTimeBucket(timeMillis);
        if (entity.getDetectPoint() != null) {
            switch (entity.getScopeType()) {
                case SERVICE_RELATION:
                    serviceRelationTraffic(entity, minute);
                    break;
                case PROCESS_RELATION:
                    processRelationTraffic(entity, minute);
                    break;
                case SERVICE_INSTANCE_RELATION:
                    serviceInstanceRelationTraffic(entity, minute);
                    break;
                default:
            }
        } else {
            toService(requireNonNull(entity.getServiceName()), entity.getLayer(), minute);
        }

        if (StringUtil.isNotEmpty(entity.getInstanceName())) {
            InstanceTraffic instanceTraffic = new InstanceTraffic();
            instanceTraffic.setName(entity.getInstanceName());
            instanceTraffic.setServiceId(entity.serviceId());
            instanceTraffic.setTimeBucket(minute);
            instanceTraffic.setLastPingTimestamp(minute);
            if (entity.getInstanceProperties() != null && !entity.getInstanceProperties().isEmpty()) {
                final JsonObject properties = new JsonObject();
                entity.getInstanceProperties().forEach(properties::addProperty);
                instanceTraffic.setProperties(properties);
            }
            MetricsStreamProcessor.getInstance().in(instanceTraffic);
        }
        if (StringUtil.isNotEmpty(entity.getEndpointName())) {
            EndpointTraffic endpointTraffic = new EndpointTraffic();
            endpointTraffic.setName(entity.getEndpointName());
            endpointTraffic.setServiceId(entity.serviceId());
            endpointTraffic.setTimeBucket(minute);
            endpointTraffic.setLastPingTimestamp(minute);
            MetricsStreamProcessor.getInstance().in(endpointTraffic);
        }
    }

    private static void toService(final String serviceName, final Layer layer, final long minute) {
        ServiceTraffic s = new ServiceTraffic();
        s.setName(requireNonNull(serviceName));
        s.setTimeBucket(minute);
        s.setLayer(layer);
        MetricsStreamProcessor.getInstance().in(s);
    }

    /**
     * Register the ends of a two-layer relation, each in its own layer: the side that reported the call, and a
     * conjectured (non-normal) peer on the other side, so a database or a broker appears in its virtual layer's
     * service list the way the agent path's ServiceMeta puts it there. A normal peer is a service with spans of its
     * own and registers itself, in its own layer; registering it from here would list an address no service has
     * claimed yet, and file an agent-instrumented peer under the caller's layer. An UNDEFINED end is a placeholder
     * rather than a service to list, {@code User} above all: the agent path's RPCAnalysisListener writes no
     * ServiceTraffic for it either, and ServiceTopologyBuilder builds its node from the relation row.
     */
    private static void toServiceEnds(final MeterEntity entity, final long minute) {
        final boolean serverSide = entity.getDetectPoint() == DetectPoint.SERVER;
        toServiceEnd(entity.getSourceServiceName(), entity.getLayer(), !serverSide, minute);
        toServiceEnd(entity.getDestServiceName(), entity.getDestLayer(), serverSide, minute);
    }

    private static void toServiceEnd(final String serviceName, final Layer layer, final boolean reporter,
                                     final long minute) {
        if (layer != Layer.UNDEFINED && (reporter || !layer.isNormal())) {
            toService(requireNonNull(serviceName), layer, minute);
        }
    }

    private static void serviceRelationTraffic(final MeterEntity entity, final long minute) {
        if (entity.getDestLayer() != null) {
            toServiceEnds(entity, minute);
        }
        switch (entity.getDetectPoint()) {
            case SERVER:
                entity.setServiceName(entity.getDestServiceName());
                if (entity.getDestLayer() == null) {
                    toService(requireNonNull(entity.getDestServiceName()), entity.getLayer(), minute);
                }
                serviceRelationServerSide(entity, minute);
                break;
            case CLIENT:
                entity.setServiceName(entity.getSourceServiceName());
                if (entity.getDestLayer() == null) {
                    toService(requireNonNull(entity.getSourceServiceName()), entity.getLayer(), minute);
                }
                serviceRelationClientSide(entity, minute);
                break;
            default:
        }
    }

    private static void serviceRelationServerSide(final MeterEntity entity, final long minute) {
        ServiceRelationServerSideMetrics metrics = new ServiceRelationServerSideMetrics();
        metrics.setTimeBucket(minute);
        metrics.setSourceServiceId(entity.sourceServiceId());
        metrics.setDestServiceId(entity.destServiceId());
        metrics.getComponentIds().add(entity.getComponentId());
        metrics.setEntityId(entity.id());
        MetricsStreamProcessor.getInstance().in(metrics);
    }

    private static void serviceRelationClientSide(final MeterEntity entity, final long minute) {
        ServiceRelationClientSideMetrics metrics = new ServiceRelationClientSideMetrics();
        metrics.setTimeBucket(minute);
        metrics.setSourceServiceId(entity.sourceServiceId());
        metrics.setDestServiceId(entity.destServiceId());
        metrics.getComponentIds().add(entity.getComponentId());
        metrics.setEntityId(entity.id());
        MetricsStreamProcessor.getInstance().in(metrics);
    }

    private static void processRelationTraffic(final MeterEntity entity, final long minute) {
        switch (entity.getDetectPoint()) {
            case SERVER:
                processRelationServerSide(entity, minute);
                break;
            case CLIENT:
                processRelationClientSide(entity, minute);
                break;
            default:
        }
    }

    private static void processRelationServerSide(final MeterEntity entity, final long minute) {
        ProcessRelationServerSideMetrics metrics = new ProcessRelationServerSideMetrics();
        metrics.setTimeBucket(minute);
        metrics.setServiceInstanceId(entity.serviceInstanceId());
        metrics.setSourceProcessId(entity.getSourceProcessId());
        metrics.setDestProcessId(entity.getDestProcessId());
        metrics.setEntityId(entity.id());
        metrics.setComponentId(entity.getComponentId());
        MetricsStreamProcessor.getInstance().in(metrics);
    }

    private static void processRelationClientSide(final MeterEntity entity, final long minute) {
        ProcessRelationClientSideMetrics metrics = new ProcessRelationClientSideMetrics();
        metrics.setTimeBucket(minute);
        metrics.setServiceInstanceId(entity.serviceInstanceId());
        metrics.setSourceProcessId(entity.getSourceProcessId());
        metrics.setDestProcessId(entity.getDestProcessId());
        metrics.setEntityId(entity.id());
        metrics.setComponentId(entity.getComponentId());
        MetricsStreamProcessor.getInstance().in(metrics);
    }

    private static void serviceInstanceRelationTraffic(final MeterEntity entity, final long minute) {
        if (entity.getDestLayer() != null) {
            toServiceEnds(entity, minute);
        }
        switch (entity.getDetectPoint()) {
            case SERVER:
                if (entity.getDestLayer() == null) {
                    toService(requireNonNull(entity.getDestServiceName()), entity.getLayer(), minute);
                }
                serviceInstanceRelationServerSide(entity, minute);
                break;
            case CLIENT:
                if (entity.getDestLayer() == null) {
                    toService(requireNonNull(entity.getSourceServiceName()), entity.getLayer(), minute);
                }
                serviceInstanceRelationClientSide(entity, minute);
                break;
            default:
        }
    }

    private static void serviceInstanceRelationServerSide(final MeterEntity entity, final long minute) {
        ServiceInstanceRelationServerSideMetrics metrics = new ServiceInstanceRelationServerSideMetrics();
        metrics.setTimeBucket(minute);
        metrics.setSourceServiceId(entity.sourceServiceId());
        metrics.setSourceServiceInstanceId(entity.sourceServiceInstanceId());
        metrics.setDestServiceId(entity.destServiceId());
        metrics.setDestServiceInstanceId(entity.destServiceInstanceId());
        metrics.setEntityId(entity.id());
        MetricsStreamProcessor.getInstance().in(metrics);
    }

    private static void serviceInstanceRelationClientSide(final MeterEntity entity, final long minute) {
        ServiceInstanceRelationClientSideMetrics metrics = new ServiceInstanceRelationClientSideMetrics();
        metrics.setTimeBucket(minute);
        metrics.setSourceServiceId(entity.sourceServiceId());
        metrics.setSourceServiceInstanceId(entity.sourceServiceInstanceId());
        metrics.setDestServiceId(entity.destServiceId());
        metrics.setDestServiceInstanceId(entity.destServiceInstanceId());
        metrics.setEntityId(entity.id());
        MetricsStreamProcessor.getInstance().in(metrics);
    }
}
