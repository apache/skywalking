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

package org.apache.skywalking.oap.query.graphql.resolver;

import graphql.kickstart.tools.GraphQLQueryResolver;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.query.TopologyQueryService;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.type.EndpointTopology;
import org.apache.skywalking.oap.server.core.query.type.ProcessTopology;
import org.apache.skywalking.oap.server.core.query.type.ServiceInstanceTopology;
import org.apache.skywalking.oap.server.core.query.type.Topology;
import org.apache.skywalking.oap.server.core.query.type.debugging.DebuggingSpan;
import org.apache.skywalking.oap.server.core.query.type.debugging.DebuggingTraceContext;
import org.apache.skywalking.oap.server.library.module.ModuleManager;

import static org.apache.skywalking.oap.query.graphql.resolver.AsyncQueryUtils.queryAsync;
import static org.apache.skywalking.oap.server.core.query.type.debugging.DebuggingTraceContext.TRACE_CONTEXT;

public class TopologyQuery implements GraphQLQueryResolver {

    private final ModuleManager moduleManager;
    private TopologyQueryService queryService;

    public TopologyQuery(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    private TopologyQueryService getQueryService() {
        if (queryService == null) {
            this.queryService = moduleManager.find(CoreModule.NAME).provider().getService(TopologyQueryService.class);
        }
        return queryService;
    }

    public CompletableFuture<Topology> getGlobalTopology(final Duration duration,
                                                         final String layer,
                                                         final boolean debug) {
        return queryAsync(() -> {
            DebuggingTraceContext traceContext = new DebuggingTraceContext(
                "Duration: " + duration + ", Layer: " + layer, debug, false);
            DebuggingTraceContext.TRACE_CONTEXT.set(traceContext);
            DebuggingSpan span = traceContext.createSpan("Query global topology");
            try {
                Topology topology = getQueryService().getGlobalTopology(duration, layer);
                if (debug) {
                    topology.setDebuggingTrace(traceContext.getExecTrace());
                }
                return topology;
            } finally {
                traceContext.stopSpan(span);
                traceContext.stopTrace();
                TRACE_CONTEXT.remove();
            }
        });
    }

    public Topology getServiceTopology(final String serviceId,
                                       final Duration duration,
                                       final boolean debug) {
        DebuggingTraceContext traceContext = new DebuggingTraceContext(
            "ServiceId: " + serviceId + "Duration: " + duration, debug, false);
        DebuggingTraceContext.TRACE_CONTEXT.set(traceContext);
        DebuggingSpan span = traceContext.createSpan("Query service topology");
        try {
            List<String> selectedServiceList = new ArrayList<>(1);
            selectedServiceList.add(serviceId);
            Topology topology = this.getServicesTopology(selectedServiceList, duration, debug).join();
            if (debug) {
                topology.setDebuggingTrace(traceContext.getExecTrace());
            }
            return topology;
        } finally {
            traceContext.stopSpan(span);
            traceContext.stopTrace();
            TRACE_CONTEXT.remove();
        }
    }

    public CompletableFuture<Topology> getServicesTopology(final List<String> serviceIds,
                                        final Duration duration,
                                        final boolean debug) {
        return queryAsync(() -> {
            DebuggingTraceContext traceContext = new DebuggingTraceContext(
                "ServiceIds: " + serviceIds + "Duration: " + duration, debug, false);
            DebuggingTraceContext.TRACE_CONTEXT.set(traceContext);
            DebuggingSpan span = traceContext.createSpan("Query service topology");
            try {
                Topology topology = getQueryService().getServiceTopology(duration, serviceIds);
                if (debug) {
                    topology.setDebuggingTrace(traceContext.getExecTrace());
                }
                return topology;
            } finally {
                traceContext.stopSpan(span);
                traceContext.stopTrace();
                TRACE_CONTEXT.remove();
            }
        });
    }

    public CompletableFuture<ServiceInstanceTopology> getServiceInstanceTopology(final String clientServiceId,
                                                              final String serverServiceId,
                                                              final Duration duration,
                                                              final boolean debug) {
        return queryAsync(() -> {
            DebuggingTraceContext traceContext = new DebuggingTraceContext(
                "ClientServiceId: " + clientServiceId + ", ServerServiceId: " + serverServiceId + ", Duration: " + duration,
                debug, false
            );
            DebuggingTraceContext.TRACE_CONTEXT.set(traceContext);
            DebuggingSpan span = traceContext.createSpan("Query service instance topology");
            try {
                ServiceInstanceTopology topology = getQueryService().getServiceInstanceTopology(
                    clientServiceId, serverServiceId,
                    duration
                );
                if (debug) {
                    topology.setDebuggingTrace(traceContext.getExecTrace());
                }
                return topology;
            } finally {
                traceContext.stopSpan(span);
                traceContext.stopTrace();
                TRACE_CONTEXT.remove();
            }
        });
    }

    /**
     * Replaced by {@link #getEndpointDependencies(String, Duration, boolean)}
     */
    @Deprecated
    public CompletableFuture<Topology> getEndpointTopology(final String endpointId, final Duration duration) {
        return queryAsync(() -> getQueryService().getEndpointTopology(duration, endpointId));
    }

    public CompletableFuture<EndpointTopology> getEndpointDependencies(final String endpointId,
                                                    final Duration duration,
                                                    final boolean debug) {
        return queryAsync(() -> {
            DebuggingTraceContext traceContext = new DebuggingTraceContext(
                "EndpointId: " + endpointId + ", Duration: " + duration, debug, false);
            DebuggingTraceContext.TRACE_CONTEXT.set(traceContext);
            DebuggingSpan span = traceContext.createSpan("Query endpoint dependencies");
            try {
                EndpointTopology topology = getQueryService().getEndpointDependencies(duration, endpointId);
                if (debug) {
                    topology.setDebuggingTrace(traceContext.getExecTrace());
                }
                return topology;
            } finally {
                traceContext.stopSpan(span);
                traceContext.stopTrace();
                TRACE_CONTEXT.remove();
            }
        });
    }

    public CompletableFuture<ProcessTopology> getProcessTopology(final String instanceId, final Duration duration, final boolean debug) {
        return queryAsync(() -> {
            DebuggingTraceContext traceContext = new DebuggingTraceContext(
                "InstanceId: " + instanceId + ", Duration: " + duration, debug, false);
            DebuggingTraceContext.TRACE_CONTEXT.set(traceContext);
            DebuggingSpan span = traceContext.createSpan("Query process topology");
            try {
                ProcessTopology topology = getQueryService().getProcessTopology(instanceId, duration);
                if (debug) {
                    topology.setDebuggingTrace(traceContext.getExecTrace());
                }
                return topology;
            } finally {
                traceContext.stopSpan(span);
                traceContext.stopTrace();
                TRACE_CONTEXT.remove();
            }
        });
    }
}
