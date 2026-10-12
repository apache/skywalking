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
        if (entity.getDetectPoint() != null) {
            switch (entity.getScopeType()) {
                case SERVICE_RELATION:
                    serviceRelationTraffic(entity);
                    break;
                case PROCESS_RELATION:
                    processRelationTraffic(entity);
                    break;
                case SERVICE_INSTANCE_RELATION:
                    serviceInstanceRelationTraffic(entity);
                    break;
                default:
            }
        } else {
            toService(requireNonNull(entity.getServiceName()), entity.getLayer());
        }

        if (StringUtil.isNotEmpty(entity.getInstanceName())) {
            InstanceTraffic instanceTraffic = new InstanceTraffic();
            instanceTraffic.setName(entity.getInstanceName());
            instanceTraffic.setServiceId(entity.serviceId());
            instanceTraffic.setTimeBucket(TimeBucket.getMinuteTimeBucket(System.currentTimeMillis()));
            instanceTraffic.setLastPingTimestamp(TimeBucket.getMinuteTimeBucket(System.currentTimeMillis()));
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
            endpointTraffic.setTimeBucket(TimeBucket.getMinuteTimeBucket(System.currentTimeMillis()));
            endpointTraffic.setLastPingTimestamp(TimeBucket.getMinuteTimeBucket(System.currentTimeMillis()));
            MetricsStreamProcessor.getInstance().in(endpointTraffic);
        }
    }

    private static void toService(final String serviceName, final Layer layer) {
        ServiceTraffic s = new ServiceTraffic();
        s.setName(requireNonNull(serviceName));
        s.setTimeBucket(TimeBucket.getMinuteTimeBucket(System.currentTimeMillis()));
        s.setLayer(layer);
        MetricsStreamProcessor.getInstance().in(s);
    }

    /**
     * Register both ends of a two-layer relation, each in its own layer, so a conjectured peer appears in its
     * virtual layer's service list the way the agent path's ServiceMeta puts it there. An UNDEFINED end is a
     * placeholder rather than a service to list, {@code User} above all: the agent path's RPCAnalysisListener
     * writes no ServiceTraffic for it either, and ServiceTopologyBuilder builds its node from the relation row.
     */
    private static void toServiceEnds(final MeterEntity entity) {
        if (entity.getLayer() != Layer.UNDEFINED) {
            toService(requireNonNull(entity.getSourceServiceName()), entity.getLayer());
        }
        if (entity.getDestLayer() != Layer.UNDEFINED) {
            toService(requireNonNull(entity.getDestServiceName()), entity.getDestLayer());
        }
    }

    private static void serviceRelationTraffic(final MeterEntity entity) {
        if (entity.getDestLayer() != null) {
            toServiceEnds(entity);
        }
        switch (entity.getDetectPoint()) {
            case SERVER:
                entity.setServiceName(entity.getDestServiceName());
                if (entity.getDestLayer() == null) {
                    toService(requireNonNull(entity.getDestServiceName()), entity.getLayer());
                }
                serviceRelationServerSide(entity);
                break;
            case CLIENT:
                entity.setServiceName(entity.getSourceServiceName());
                if (entity.getDestLayer() == null) {
                    toService(requireNonNull(entity.getSourceServiceName()), entity.getLayer());
                }
                serviceRelationClientSide(entity);
                break;
            default:
        }
    }

    private static void serviceRelationServerSide(final MeterEntity entity) {
        ServiceRelationServerSideMetrics metrics = new ServiceRelationServerSideMetrics();
        metrics.setTimeBucket(TimeBucket.getMinuteTimeBucket(System.currentTimeMillis()));
        metrics.setSourceServiceId(entity.sourceServiceId());
        metrics.setDestServiceId(entity.destServiceId());
        metrics.getComponentIds().add(entity.getComponentId());
        metrics.setEntityId(entity.id());
        MetricsStreamProcessor.getInstance().in(metrics);
    }

    private static void serviceRelationClientSide(final MeterEntity entity) {
        ServiceRelationClientSideMetrics metrics = new ServiceRelationClientSideMetrics();
        metrics.setTimeBucket(TimeBucket.getMinuteTimeBucket(System.currentTimeMillis()));
        metrics.setSourceServiceId(entity.sourceServiceId());
        metrics.setDestServiceId(entity.destServiceId());
        metrics.getComponentIds().add(entity.getComponentId());
        metrics.setEntityId(entity.id());
        MetricsStreamProcessor.getInstance().in(metrics);
    }

    private static void processRelationTraffic(final MeterEntity entity) {
        switch (entity.getDetectPoint()) {
            case SERVER:
                processRelationServerSide(entity);
                break;
            case CLIENT:
                processRelationClientSide(entity);
                break;
            default:
        }
    }

    private static void processRelationServerSide(final MeterEntity entity) {
        ProcessRelationServerSideMetrics metrics = new ProcessRelationServerSideMetrics();
        metrics.setTimeBucket(TimeBucket.getMinuteTimeBucket(System.currentTimeMillis()));
        metrics.setServiceInstanceId(entity.serviceInstanceId());
        metrics.setSourceProcessId(entity.getSourceProcessId());
        metrics.setDestProcessId(entity.getDestProcessId());
        metrics.setEntityId(entity.id());
        metrics.setComponentId(entity.getComponentId());
        MetricsStreamProcessor.getInstance().in(metrics);
    }

    private static void processRelationClientSide(final MeterEntity entity) {
        ProcessRelationClientSideMetrics metrics = new ProcessRelationClientSideMetrics();
        metrics.setTimeBucket(TimeBucket.getMinuteTimeBucket(System.currentTimeMillis()));
        metrics.setServiceInstanceId(entity.serviceInstanceId());
        metrics.setSourceProcessId(entity.getSourceProcessId());
        metrics.setDestProcessId(entity.getDestProcessId());
        metrics.setEntityId(entity.id());
        metrics.setComponentId(entity.getComponentId());
        MetricsStreamProcessor.getInstance().in(metrics);
    }

    private static void serviceInstanceRelationTraffic(final MeterEntity entity) {
        if (entity.getDestLayer() != null) {
            toServiceEnds(entity);
        }
        switch (entity.getDetectPoint()) {
            case SERVER:
                if (entity.getDestLayer() == null) {
                    toService(requireNonNull(entity.getDestServiceName()), entity.getLayer());
                }
                serviceInstanceRelationServerSide(entity);
                break;
            case CLIENT:
                if (entity.getDestLayer() == null) {
                    toService(requireNonNull(entity.getSourceServiceName()), entity.getLayer());
                }
                serviceInstanceRelationClientSide(entity);
                break;
            default:
        }
    }

    private static void serviceInstanceRelationServerSide(final MeterEntity entity) {
        ServiceInstanceRelationServerSideMetrics metrics = new ServiceInstanceRelationServerSideMetrics();
        metrics.setTimeBucket(TimeBucket.getMinuteTimeBucket(System.currentTimeMillis()));
        metrics.setSourceServiceId(entity.sourceServiceId());
        metrics.setSourceServiceInstanceId(entity.sourceServiceInstanceId());
        metrics.setDestServiceId(entity.destServiceId());
        metrics.setDestServiceInstanceId(entity.destServiceInstanceId());
        metrics.setEntityId(entity.id());
        MetricsStreamProcessor.getInstance().in(metrics);
    }

    private static void serviceInstanceRelationClientSide(final MeterEntity entity) {
        ServiceInstanceRelationClientSideMetrics metrics = new ServiceInstanceRelationClientSideMetrics();
        metrics.setTimeBucket(TimeBucket.getMinuteTimeBucket(System.currentTimeMillis()));
        metrics.setSourceServiceId(entity.sourceServiceId());
        metrics.setSourceServiceInstanceId(entity.sourceServiceInstanceId());
        metrics.setDestServiceId(entity.destServiceId());
        metrics.setDestServiceInstanceId(entity.destServiceInstanceId());
        metrics.setEntityId(entity.id());
        MetricsStreamProcessor.getInstance().in(metrics);
    }
}
