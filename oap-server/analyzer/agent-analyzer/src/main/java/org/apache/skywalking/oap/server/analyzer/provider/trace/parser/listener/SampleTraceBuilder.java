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

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.apache.skywalking.oap.server.core.analysis.DownSampling;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.manual.trace.SampleTraceSlowRecord;
import org.apache.skywalking.oap.server.core.analysis.record.Record;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.core.source.DetectPoint;
import org.apache.skywalking.oap.server.core.source.ISource;
import org.apache.skywalking.oap.server.core.source.ProcessRelation;
import org.apache.skywalking.oap.server.library.util.StringUtil;

@RequiredArgsConstructor
public class SampleTraceBuilder {
    private final NamingControl namingControl;

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

    public String validate() {
        String result = null;
        result = validateNotEmpty(result, "traceId", traceId);
        result = validateBiggerZero(result, "latency", latency);
        result = validateNotEmpty(result, "uri", uri);
        result = validateNotEmpty(result, "reason", reason);
        result = validateNotEmpty(result, "layer", layer);
        result = validateNotEmpty(result, "service name", serviceName);
        result = validateNotEmpty(result, "service instance name", serviceInstanceName);
        result = validateNotEmpty(result, "process id", processId);
        result = validateNotEmpty(result, "dest process id", destProcessId);
        result = validateNotEmpty(result, "detect point", detectPoint);
        result = validateNotEmpty(result, "dest process id", destProcessId);
        result = validateBiggerZero(result, "component id", componentId);
        result = validateBiggerZero(result, "timestamp", timestamp);
        return result;
    }

    public Record toRecord() {
        final SampleTraceSlowRecord record = new SampleTraceSlowRecord();
        record.setScope(DefaultScopeDefine.PROCESS_RELATION);
        record.setEntityId(IDManager.ProcessID.buildRelationId(new IDManager.ProcessID.ProcessRelationDefine(
            processId, destProcessId
        )));
        record.setTraceId(traceId);
        record.setUri(uri);
        record.setLatency(latency);
        record.setTimeBucket(TimeBucket.getTimeBucket(timestamp, DownSampling.Second));
        return record;
    }

    public ISource toEntity() {
        final ProcessRelation processRelation = new ProcessRelation();
        final String serviceId = IDManager.ServiceID.buildId(namingControl.formatServiceName(serviceName),
            Layer.nameOf(layer).isNormal());
        final String instanceId = IDManager.ServiceInstanceID.buildId(serviceId, namingControl.formatInstanceName(serviceInstanceName));
        processRelation.setInstanceId(instanceId);
        processRelation.setSourceProcessId(processId);
        processRelation.setDestProcessId(destProcessId);
        processRelation.setDetectPoint(detectPoint);
        processRelation.setComponentId(componentId);
        return processRelation;
    }

    private String validateNotEmpty(String error, String name, String message) {
        if (error != null) {
            return error;
        }
        if (StringUtil.isEmpty(message)) {
            return name + " not configured.";
        }
        return null;
    }

    private String validateNotEmpty(String error, String name, Object data) {
        if (error != null) {
            return error;
        }
        if (data == null) {
            return name + " not configured.";
        }
        return null;
    }

    private String validateBiggerZero(String error, String name, long val) {
        if (error != null) {
            return error;
        }
        if (val < 0) {
            return name + " must bigger zero.";
        }
        return null;
    }

    /**
     * The reason of sampled trace.
     */
    public enum Reason {
        SLOW
    }
}