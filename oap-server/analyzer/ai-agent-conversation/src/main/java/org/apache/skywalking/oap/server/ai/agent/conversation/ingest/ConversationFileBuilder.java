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

package org.apache.skywalking.oap.server.ai.agent.conversation.ingest;

import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import javax.annotation.Nullable;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.network.common.v3.KeyStringValuePair;
import org.apache.skywalking.apm.network.logging.v3.LogData;
import org.apache.skywalking.oap.server.ai.agent.conversation.format.Digests;
import org.apache.skywalking.oap.server.ai.agent.conversation.format.Times;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.manual.aiagent.AIAgentSessionDataRecord;
import org.apache.skywalking.oap.server.core.analysis.manual.aiagent.AIAgentSessionFlowRecord;
import org.apache.skywalking.oap.server.core.analysis.record.Record;
import org.apache.skywalking.oap.server.core.analysis.worker.RecordStreamProcessor;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.source.LALOutputBuilder;
import org.apache.skywalking.oap.server.core.source.LogMetadata;
import org.apache.skywalking.oap.server.core.source.SourceReceiver;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.oap.server.telemetry.api.CounterMetrics;

/**
 * The LAL output builder named by <code>outputType: ConversationFile</code> in <code>lal/ai-agent.yaml</code>.
 *
 * <p>The rule's extractor sets the fields below from the record's <code>asz.*</code> attributes, then
 * {@link #init} takes the body and the service, instance and timestamp the OTLP handler already resolved, and
 * {@link #complete} verifies the file and dispatches one row to the Session Data or the Session Flow table. One
 * rule, one builder: the branch on <code>format</code> lives here.
 *
 * <p>The service and instance traffic is registered by the log analyzer's own traffic listener for every record
 * the rule keeps, so this builder registers none.
 */
@Slf4j
public class ConversationFileBuilder implements LALOutputBuilder {
    public static final String NAME = "ConversationFile";
    private static final String FORMAT_SD = "sd";
    private static final String FORMAT_SF = "sf";

    private static volatile CounterMetrics ACCEPTED_DATA;
    private static volatile CounterMetrics ACCEPTED_FLOW;
    private static volatile CounterMetrics REJECTED_DIGEST;
    private static volatile CounterMetrics REJECTED_LINES;
    private static volatile CounterMetrics REJECTED_ATTRIBUTES;
    private static volatile NamingControl NAMING_CONTROL;

    // every record
    @Getter
    @Setter
    private String format;
    @Getter
    @Setter
    private String digest;
    /** -1 until the extractor sets it; the LAL compiler generates primitive setter calls. */
    @Getter
    @Setter
    private long lines = -1;
    @Getter
    @Setter
    private String throughTime;
    // sd
    @Getter
    @Setter
    private String session;
    @Getter
    @Setter
    private long seq = -1;
    // sf
    @Getter
    @Setter
    private String conversation;
    @Getter
    @Setter
    private long round = -1;
    @Getter
    @Setter
    private String sessionFromTime;
    @Getter
    @Setter
    private String sessionThroughTime;
    @Getter
    @Setter
    private String title;
    @Getter
    @Setter
    private long talks;
    @Getter
    @Setter
    private long steps;
    @Getter
    @Setter
    private long streams;
    @Getter
    @Setter
    private long segments;
    @Getter
    @Setter
    private long unresolved;

    // from the handler, through init
    private String serviceName;
    private String instanceName;
    private Layer layer = Layer.AI_AGENT;
    private long timestamp;
    private byte[] body;
    private String fileName;

    /**
     * Set once by the module provider; the builder itself is created per record by the LAL runtime.
     *
     * @param dataAccepted  files stored to the Session Data table
     * @param flowAccepted  rounds stored to the Session Flow table
     * @param digest        files rejected because the body's digest is not the declared one
     * @param lines         files rejected because the body's line count is not the declared one
     * @param attributes    files rejected because a required attribute is missing
     */
    public static void setMetrics(final CounterMetrics dataAccepted,
                                  final CounterMetrics flowAccepted,
                                  final CounterMetrics digest,
                                  final CounterMetrics lines,
                                  final CounterMetrics attributes) {
        ACCEPTED_DATA = dataAccepted;
        ACCEPTED_FLOW = flowAccepted;
        REJECTED_DIGEST = digest;
        REJECTED_LINES = lines;
        REJECTED_ATTRIBUTES = attributes;
    }

    /**
     * Normally resolved from the core module on the first record; a test sets it directly.
     *
     * @param control the naming control
     */
    public static void setNamingControl(final NamingControl control) {
        NAMING_CONTROL = control;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void init(final LogMetadata metadata, final Object input, final ModuleManager moduleManager) {
        if (NAMING_CONTROL == null && moduleManager != null) {
            NAMING_CONTROL = moduleManager.find(CoreModule.NAME).provider().getService(NamingControl.class);
        }
        serviceName = metadata.getService();
        instanceName = metadata.getServiceInstance();
        if (StringUtil.isNotEmpty(metadata.getLayer())) {
            layer = Layer.valueOf(metadata.getLayer());
        }
        timestamp = metadata.getTimestamp();
        if (input instanceof LogData.Builder) {
            final LogData.Builder logData = (LogData.Builder) input;
            body = logData.getBody().getText().getText().getBytes(StandardCharsets.UTF_8);
            for (final KeyStringValuePair tag : logData.getTags().getDataList()) {
                if ("asz.file".equals(tag.getKey())) {
                    fileName = tag.getValue();
                }
            }
        }
    }

    @Override
    public void complete(final SourceReceiver sourceReceiver) {
        if (body == null || StringUtil.isEmpty(format) || StringUtil.isEmpty(digest) || lines < 0) {
            reject(REJECTED_ATTRIBUTES, "a required attribute is missing");
            return;
        }
        if (!digest.equals(Digests.sha256Hex(body))) {
            reject(REJECTED_DIGEST, "the body's digest is not the declared " + digest);
            return;
        }
        if (Digests.countLines(body) != lines) {
            reject(REJECTED_LINES, "the body has " + Digests.countLines(body) + " lines, the record declares " + lines);
            return;
        }
        final String formattedService = NAMING_CONTROL.formatServiceName(serviceName);
        final String serviceId = IDManager.ServiceID.buildId(formattedService, layer.isNormal());
        final String instanceId = IDManager.ServiceInstanceID.buildId(
            serviceId, NAMING_CONTROL.formatInstanceName(StringUtil.isEmpty(instanceName) ? "unknown" : instanceName));
        switch (format) {
            case FORMAT_SD:
                if (StringUtil.isEmpty(session) || seq < 0) {
                    reject(REJECTED_ATTRIBUTES, "a Session Data record without asz.session or asz.seq");
                    return;
                }
                dispatch(dataRecord(serviceId, instanceId));
                count(ACCEPTED_DATA);
                break;
            case FORMAT_SF:
                if (StringUtil.isEmpty(conversation) || round < 0) {
                    reject(REJECTED_ATTRIBUTES, "a Session Flow record without asz.conversation or asz.round");
                    return;
                }
                dispatch(flowRecord(serviceId, instanceId));
                count(ACCEPTED_FLOW);
                break;
            default:
                reject(REJECTED_ATTRIBUTES, "unknown asz.format " + format);
                break;
        }
    }

    /**
     * Hands a verified row to the record stream. A test overrides it to capture the row.
     *
     * @param record the verified row
     */
    protected void dispatch(final Record record) {
        RecordStreamProcessor.getInstance().in(record);
    }

    private AIAgentSessionDataRecord dataRecord(final String serviceId, final String instanceId) {
        final AIAgentSessionDataRecord record = new AIAgentSessionDataRecord();
        record.setServiceId(serviceId);
        record.setServiceInstanceId(instanceId);
        record.setSession(session);
        record.setSeq(seq);
        record.setDigest(digest);
        // The file's own through time places it inside the conversation's range; the record's timestamp is the
        // sender's fallback for a file whose records carry no time.
        final long fileTime = Times.millis(throughTime);
        final long ts = fileTime != 0 ? fileTime : timestamp;
        record.setTimestamp(ts);
        record.setTimeBucket(TimeBucket.getRecordTimeBucket(ts));
        record.setBody(body);
        return record;
    }

    private AIAgentSessionFlowRecord flowRecord(final String serviceId, final String instanceId) {
        final AIAgentSessionFlowRecord record = new AIAgentSessionFlowRecord();
        record.setServiceId(serviceId);
        record.setServiceInstanceId(instanceId);
        record.setConversation(conversation);
        record.setRound(round);
        record.setSessionFromTime(Times.millis(sessionFromTime));
        record.setTitle(clip(title, AIAgentSessionFlowRecord.TITLE_MAX_LENGTH));
        record.setTalks(talks);
        record.setSteps(steps);
        record.setStreams(streams);
        record.setSegments(segments);
        record.setUnresolved(unresolved);
        record.setDigest(digest);
        // The conversation's last activity as of this round is the row's time, so the newest row is the head.
        final long through = Times.millis(sessionThroughTime);
        final long ts = through != 0 ? through : timestamp;
        record.setTimestamp(ts);
        record.setTimeBucket(TimeBucket.getRecordTimeBucket(ts));
        record.setBody(body);
        return record;
    }

    private void reject(@Nullable final CounterMetrics counter, final String why) {
        count(counter);
        log.warn("AI agent conversation file {} from {}/{} rejected: {}", fileName, serviceName, instanceName, why);
    }

    private static void count(@Nullable final CounterMetrics counter) {
        if (counter != null) {
            counter.inc();
        }
    }

    @Nullable
    private static String clip(@Nullable final String value, final int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    @Override
    public String outputToJson() {
        final JsonObject obj = new JsonObject();
        obj.addProperty("type", getClass().getSimpleName());
        obj.addProperty("name", name());
        obj.addProperty("format", format);
        obj.addProperty("file", fileName);
        obj.addProperty("digest", digest);
        obj.addProperty("lines", lines);
        obj.addProperty("session", session);
        obj.addProperty("seq", seq);
        obj.addProperty("conversation", conversation);
        obj.addProperty("round", round);
        obj.addProperty("title", title);
        obj.addProperty("serviceName", serviceName);
        obj.addProperty("instanceName", instanceName);
        obj.addProperty("timestamp", timestamp);
        obj.addProperty("bytes", body == null ? 0 : body.length);
        return obj.toString();
    }
}
