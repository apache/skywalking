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

package org.apache.skywalking.oap.server.analyzer.provider.trace.parser.listener;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.analysis.DownSampling;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.manual.trace.SampledSlowTraceRecord;
import org.apache.skywalking.oap.server.core.analysis.manual.trace.SampledStatus4xxTraceRecord;
import org.apache.skywalking.oap.server.core.analysis.manual.trace.SampledStatus5xxTraceRecord;
import org.apache.skywalking.oap.server.core.analysis.record.Record;
import org.apache.skywalking.oap.server.core.analysis.worker.RecordStreamProcessor;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.core.source.DetectPoint;
import org.apache.skywalking.oap.server.core.source.ISource;
import org.apache.skywalking.oap.server.core.source.LALOutputBuilder;
import org.apache.skywalking.oap.server.core.source.LogMetadata;
import org.apache.skywalking.oap.server.core.source.ProcessRelation;
import org.apache.skywalking.oap.server.core.source.SourceReceiver;
import org.apache.skywalking.oap.server.library.module.ModuleManager;

@Slf4j
public class SampledTraceBuilder implements LALOutputBuilder {
    public static final String NAME = "SampledTrace";

    private static NamingControl NAMING_CONTROL;
    private static boolean INITIALIZED;

    @Setter
    @Getter
    private String traceId;
    @Setter
    @Getter
    private String uri;
    @Setter
    @Getter
    private long latency;
    @Setter
    @Getter
    private Reason reason;

    @Setter
    @Getter
    private String layer;
    @Setter
    @Getter
    private String serviceName;
    @Setter
    @Getter
    private String serviceInstanceName;
    @Setter
    @Getter
    private String processId;
    @Setter
    @Getter
    private String destProcessId;
    @Setter
    @Getter
    private int componentId;
    @Setter
    @Getter
    private DetectPoint detectPoint;

    @Setter
    @Getter
    private long timestamp;

    public SampledTraceBuilder() {
    }

    /**
     * Constructor for v1 (Groovy) path where NamingControl is resolved eagerly.
     */
    public SampledTraceBuilder(final NamingControl namingControl) {
        NAMING_CONTROL = namingControl;
        INITIALIZED = true;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void init(final LogMetadata metadata, final Object input,
                     final ModuleManager moduleManager) {
        if (!INITIALIZED) {
            NAMING_CONTROL = moduleManager.find(CoreModule.NAME)
                                          .provider()
                                          .getService(NamingControl.class);
            INITIALIZED = true;
        }
        // Only populate fields not already set by the LAL extractor.
        if (this.traceId == null) {
            final LogMetadata.TraceContext tc = metadata.getTraceContext();
            if (tc != null) {
                this.traceId = tc.getTraceId();
            }
        }
        if (this.serviceName == null) {
            this.serviceName = metadata.getService();
        }
        if (this.serviceInstanceName == null) {
            this.serviceInstanceName = metadata.getServiceInstance();
        }
        if (this.layer == null && metadata.getLayer() != null && !metadata.getLayer().isEmpty()) {
            this.layer = metadata.getLayer();
        }
        if (this.timestamp == 0) {
            this.timestamp = metadata.getTimestamp();
        }
    }

    @Override
    public void complete(final SourceReceiver sourceReceiver) {
        if (Strings.isNullOrEmpty(traceId) || reason == null
                || Strings.isNullOrEmpty(processId) || Strings.isNullOrEmpty(destProcessId)
                || componentId <= 0 || detectPoint == null || timestamp <= 0) {
            if (log.isDebugEnabled()) {
                log.debug("SampledTrace builder incomplete, skipping dispatch: traceId={}, reason={}, "
                        + "processId={}, destProcessId={}, componentId={}, detectPoint={}, timestamp={}",
                    traceId, reason, processId, destProcessId, componentId, detectPoint, timestamp);
            }
            return;
        }
        if (log.isDebugEnabled()) {
            log.debug("SampledTrace builder dispatching: service={}, traceId={}, uri={}, reason={}",
                serviceName, traceId, uri, reason);
        }
        validate();
        final Record record = toRecord();
        RecordStreamProcessor.getInstance().in(record);
        sourceReceiver.receive(toEntity());
    }

    public void validate() {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(traceId), "traceId can't be empty");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(uri), "uri can't be empty");
        Preconditions.checkArgument(latency >= 0, "latency must bigger or equals zero");
        Preconditions.checkArgument(reason != null, "reason can't be empty");
        Preconditions.checkArgument(layer != null, "layer can't be empty");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(serviceName), "service name can't be empty");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(serviceInstanceName), "service instance name can't be empty");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(processId), "processId can't be empty");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(destProcessId), "destProcessId can't be empty");
        Preconditions.checkArgument(componentId > 0, "componentId must bigger zero");
        Preconditions.checkArgument(detectPoint != null, "detestPoint can't be empty");
        Preconditions.checkArgument(timestamp > 0, "timestamp must bigger zero");
    }

    public Record toRecord() {
        switch (this.reason) {
            case SLOW:
                final SampledSlowTraceRecord slowTraceRecord = new SampledSlowTraceRecord();
                slowTraceRecord.setScope(DefaultScopeDefine.PROCESS_RELATION);
                slowTraceRecord.setEntityId(IDManager.ProcessID.buildRelationId(new IDManager.ProcessID.ProcessRelationDefine(
                    processId, destProcessId
                )));
                slowTraceRecord.setTraceId(traceId);
                slowTraceRecord.setUri(uri);
                slowTraceRecord.setLatency(latency);
                slowTraceRecord.setTimeBucket(TimeBucket.getTimeBucket(timestamp, DownSampling.Second));
                slowTraceRecord.setTimestamp(timestamp);
                return slowTraceRecord;
            case STATUS_4XX:
                final SampledStatus4xxTraceRecord status4xxTraceRecord = new SampledStatus4xxTraceRecord();
                status4xxTraceRecord.setScope(DefaultScopeDefine.PROCESS_RELATION);
                status4xxTraceRecord.setEntityId(IDManager.ProcessID.buildRelationId(new IDManager.ProcessID.ProcessRelationDefine(
                    processId, destProcessId
                )));
                status4xxTraceRecord.setTraceId(traceId);
                status4xxTraceRecord.setUri(uri);
                status4xxTraceRecord.setLatency(latency);
                status4xxTraceRecord.setTimeBucket(TimeBucket.getTimeBucket(timestamp, DownSampling.Second));
                status4xxTraceRecord.setTimestamp(timestamp);
                return status4xxTraceRecord;
            case STATUS_5XX:
                final SampledStatus5xxTraceRecord status5xxTraceRecord = new SampledStatus5xxTraceRecord();
                status5xxTraceRecord.setScope(DefaultScopeDefine.PROCESS_RELATION);
                status5xxTraceRecord.setEntityId(IDManager.ProcessID.buildRelationId(new IDManager.ProcessID.ProcessRelationDefine(
                    processId, destProcessId
                )));
                status5xxTraceRecord.setTraceId(traceId);
                status5xxTraceRecord.setUri(uri);
                status5xxTraceRecord.setLatency(latency);
                status5xxTraceRecord.setTimeBucket(TimeBucket.getTimeBucket(timestamp, DownSampling.Second));
                status5xxTraceRecord.setTimestamp(timestamp);
                return status5xxTraceRecord;
            default:
                throw new IllegalArgumentException("unknown reason: " + this.reason);
        }
    }

    public ISource toEntity() {
        final ProcessRelation processRelation = new ProcessRelation();
        final String serviceId = IDManager.ServiceID.buildId(NAMING_CONTROL.formatServiceName(serviceName),
            Layer.nameOf(layer).isNormal());
        final String instanceId = IDManager.ServiceInstanceID.buildId(serviceId, NAMING_CONTROL.formatInstanceName(serviceInstanceName));
        processRelation.setInstanceId(instanceId);
        processRelation.setSourceProcessId(processId);
        processRelation.setDestProcessId(destProcessId);
        processRelation.setDetectPoint(detectPoint);
        processRelation.setComponentId(componentId);
        return processRelation;
    }

    /**
     * The reason of sampled trace.
     */
    public enum Reason {
        SLOW,
        STATUS_4XX,
        STATUS_5XX
    }
}
