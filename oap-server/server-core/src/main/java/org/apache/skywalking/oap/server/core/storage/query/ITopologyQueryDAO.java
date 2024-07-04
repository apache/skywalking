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

package org.apache.skywalking.oap.server.core.storage.query;

import java.io.IOException;
import java.util.List;
import org.apache.skywalking.oap.server.core.analysis.manual.relation.instance.ServiceInstanceRelationClientSideMetrics;
import org.apache.skywalking.oap.server.core.analysis.manual.relation.instance.ServiceInstanceRelationServerSideMetrics;
import org.apache.skywalking.oap.server.core.analysis.manual.relation.service.ServiceRelationClientSideMetrics;
import org.apache.skywalking.oap.server.core.analysis.manual.relation.service.ServiceRelationServerSideMetrics;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.type.Call;
import org.apache.skywalking.oap.server.core.query.type.debugging.DebuggingSpan;
import org.apache.skywalking.oap.server.core.query.type.debugging.DebuggingTraceContext;
import org.apache.skywalking.oap.server.library.module.Service;

public interface ITopologyQueryDAO extends Service {
    default List<Call.CallDetail> loadServiceRelationsDetectedAtServerSideDebuggable(Duration duration) throws IOException {
        DebuggingTraceContext traceContext = DebuggingTraceContext.TRACE_CONTEXT.get();
        DebuggingSpan span = null;
        try {
            if (traceContext != null) {
                span = traceContext.createSpan("Query Dao: loadServiceRelationsDetectedAtServerSide");
                span.setMsg("Duration: " + duration);
            }
            return loadServiceRelationsDetectedAtServerSide(duration);
        } finally {
            if (traceContext != null && span != null) {
                traceContext.stopSpan(span);
            }
        }
    }

    default List<Call.CallDetail> loadServiceRelationDetectedAtClientSideDebuggable(Duration duration) throws IOException {
        DebuggingTraceContext traceContext = DebuggingTraceContext.TRACE_CONTEXT.get();
        DebuggingSpan span = null;
        try {
            if (traceContext != null) {
                span = traceContext.createSpan("Query Dao: loadServiceRelationDetectedAtClientSide");
                span.setMsg("Duration: " + duration);
            }
            return loadServiceRelationDetectedAtClientSide(duration);
        } finally {
            if (traceContext != null && span != null) {
                traceContext.stopSpan(span);
            }
        }
    }

    default List<Call.CallDetail> loadServiceRelationsDetectedAtServerSideDebuggable(Duration duration,
                                                                               List<String> serviceIds) throws IOException {
        DebuggingTraceContext traceContext = DebuggingTraceContext.TRACE_CONTEXT.get();
        DebuggingSpan span = null;
        try {
            if (traceContext != null) {
                span = traceContext.createSpan("Query Dao: loadServiceRelationsDetectedAtServerSide");
                span.setMsg("Duration: " + duration + ", ServiceIds: " + serviceIds);
            }
            return loadServiceRelationsDetectedAtServerSide(duration, serviceIds);
        } finally {
            if (traceContext != null && span != null) {
                traceContext.stopSpan(span);
            }
        }
    }

    default List<Call.CallDetail> loadServiceRelationDetectedAtClientSideDebuggable(Duration duration,
                                                                              List<String> serviceIds) throws IOException {
        DebuggingTraceContext traceContext = DebuggingTraceContext.TRACE_CONTEXT.get();
        DebuggingSpan span = null;
        try {
            if (traceContext != null) {
                span = traceContext.createSpan("Query Dao: loadServiceRelationDetectedAtClientSide");
                span.setMsg("Duration: " + duration + ", ServiceIds: " + serviceIds);
            }
            return loadServiceRelationDetectedAtClientSide(duration, serviceIds);
        } finally {
            if (traceContext != null && span != null) {
                traceContext.stopSpan(span);
            }
        }
    }

    default List<Call.CallDetail> loadInstanceRelationDetectedAtServerSideDebuggable(String clientServiceId,
                                                                               String serverServiceId,
                                                                               Duration duration) throws IOException {
        DebuggingTraceContext traceContext = DebuggingTraceContext.TRACE_CONTEXT.get();
        DebuggingSpan span = null;
        try {
            if (traceContext != null) {
                span = traceContext.createSpan("Query Dao: loadInstanceRelationDetectedAtServerSide");
                span.setMsg("ClientServiceId: " + clientServiceId + ", ServerServiceId: " + serverServiceId + ", Duration: " + duration);
            }
            return loadInstanceRelationDetectedAtServerSide(clientServiceId, serverServiceId, duration);
        } finally {
            if (traceContext != null && span != null) {
                traceContext.stopSpan(span);
            }
        }
    }

    default List<Call.CallDetail> loadInstanceRelationDetectedAtClientSideDebuggable(String clientServiceId,
                                                                               String serverServiceId,
                                                                               Duration duration) throws IOException {
        DebuggingTraceContext traceContext = DebuggingTraceContext.TRACE_CONTEXT.get();
        DebuggingSpan span = null;
        try {
            if (traceContext != null) {
                span = traceContext.createSpan("Query Dao: loadInstanceRelationDetectedAtClientSide");
                span.setMsg("ClientServiceId: " + clientServiceId + ", ServerServiceId: " + serverServiceId + ", Duration: " + duration);
            }
            return loadInstanceRelationDetectedAtClientSide(clientServiceId, serverServiceId, duration);
        } finally {
            if (traceContext != null && span != null) {
                traceContext.stopSpan(span);
            }
        }
    }

    default List<Call.CallDetail> loadEndpointRelationDebuggable(Duration duration,
                                               String destEndpointId) throws IOException {
        DebuggingTraceContext traceContext = DebuggingTraceContext.TRACE_CONTEXT.get();
        DebuggingSpan span = null;
        try {
            if (traceContext != null) {
                span = traceContext.createSpan("Query Dao: loadEndpointRelation");
                span.setMsg("Duration: " + duration + ", DestEndpointId: " + destEndpointId);
            }
            return loadEndpointRelation(duration, destEndpointId);
        } finally {
            if (traceContext != null && span != null) {
                traceContext.stopSpan(span);
            }
        }
    }

    default List<Call.CallDetail> loadProcessRelationDetectedAtClientSideDebuggable(String serviceInstanceId,
                                                                  Duration duration) throws IOException {
        DebuggingTraceContext traceContext = DebuggingTraceContext.TRACE_CONTEXT.get();
        DebuggingSpan span = null;
        try {
            if (traceContext != null) {
                span = traceContext.createSpan("Query Dao: loadProcessRelationDetectedAtClientSide");
                span.setMsg("ServiceInstanceId: " + serviceInstanceId + ", Duration: " + duration);
            }
            return loadProcessRelationDetectedAtClientSide(serviceInstanceId, duration);
        } finally {
            if (traceContext != null && span != null) {
                traceContext.stopSpan(span);
            }
        }
    }

    default List<Call.CallDetail> loadProcessRelationDetectedAtServerSideDebuggable(String serviceInstanceId,
                                                                  Duration duration) throws IOException {
        DebuggingTraceContext traceContext = DebuggingTraceContext.TRACE_CONTEXT.get();
        DebuggingSpan span = null;
        try {
            if (traceContext != null) {
                span = traceContext.createSpan("Query Dao: loadProcessRelationDetectedAtServerSide");
                span.setMsg("ServiceInstanceId: " + serviceInstanceId + ", Duration: " + duration);
            }
            return loadProcessRelationDetectedAtServerSide(serviceInstanceId, duration);
        } finally {
            if (traceContext != null && span != null) {
                traceContext.stopSpan(span);
            }
        }
    }

    /**
     * Query {@link ServiceRelationServerSideMetrics} through the given conditions
     */
    List<Call.CallDetail> loadServiceRelationsDetectedAtServerSide(Duration duration,
                                                                   List<String> serviceIds) throws IOException;

    /**
     * Query {@link ServiceRelationClientSideMetrics} through the given conditions
     */
    List<Call.CallDetail> loadServiceRelationDetectedAtClientSide(Duration duration,
                                                                  List<String> serviceIds) throws IOException;

    /**
     * Query {@link ServiceRelationServerSideMetrics} globally, without given serviceIds
     */
    List<Call.CallDetail> loadServiceRelationsDetectedAtServerSide(Duration duration) throws IOException;

    /**
     * Query {@link ServiceRelationClientSideMetrics} globally, without given serviceIds
     */
    List<Call.CallDetail> loadServiceRelationDetectedAtClientSide(Duration duration) throws IOException;

    /**
     * Query {@link ServiceInstanceRelationServerSideMetrics} through given conditions, including the specific
     * clientServiceId and serverServiceId
     */
    List<Call.CallDetail> loadInstanceRelationDetectedAtServerSide(String clientServiceId,
                                                                   String serverServiceId,
                                                                   Duration duration) throws IOException;

    /**
     * Query {@link ServiceInstanceRelationClientSideMetrics} through given conditions, including the specific
     * clientServiceId and serverServiceId
     */
    List<Call.CallDetail> loadInstanceRelationDetectedAtClientSide(String clientServiceId,
                                                                   String serverServiceId,
                                                                   Duration duration) throws IOException;

    /**
     * Query the endpoint relationship. Endpoint dependency is not detected from server side agent.
     */
    List<Call.CallDetail> loadEndpointRelation(Duration duration,
                                               String destEndpointId) throws IOException;

    /**
     * Query {@link org.apache.skywalking.oap.server.core.analysis.manual.relation.process.ProcessRelationClientSideMetrics}
     * through given conditions, including the specific service instance id
     */
    List<Call.CallDetail> loadProcessRelationDetectedAtClientSide(String serviceInstanceId,
                                                                  Duration duration) throws IOException;

    /**
     * Query {@link org.apache.skywalking.oap.server.core.analysis.manual.relation.process.ProcessRelationServerSideMetrics}
     * through given conditions, including the specific service instance id
     */
    List<Call.CallDetail> loadProcessRelationDetectedAtServerSide(String serviceInstanceId,
                                                                  Duration duration) throws IOException;
}
