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

package org.apache.skywalking.oap.server.core.source;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.network.logging.v3.LogData;
import org.apache.skywalking.apm.network.logging.v3.LogDataBody;
import org.apache.skywalking.apm.network.logging.v3.TraceContext;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.Tag;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.TagType;
import org.apache.skywalking.oap.server.core.config.ConfigService;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.query.type.ContentType;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.util.StringUtil;

/**
 * Default LAL output builder that produces a {@link Log} source object.
 * Used when no explicit {@code outputType} is configured in the LAL rule.
 */
@Slf4j
public class LogBuilder implements LALOutputBuilder {
    public static final String NAME = "Log";

    private static NamingControl NAMING_CONTROL;
    private static List<String> SEARCHABLE_TAG_KEYS;
    private static boolean INITIALIZED;

    private LogData logData;

    @Setter
    private String service;
    @Setter
    private String serviceInstance;
    @Setter
    private String endpoint;
    @Setter
    private String layer;
    @Setter
    private String traceId;
    @Setter
    private String segmentId;
    @Getter
    @Setter
    private long timestamp;

    private int spanId = -1;
    private final List<String[]> lalTags = new ArrayList<>();

    public void setSpanId(final String spanId) {
        if (spanId != null) {
            this.spanId = Integer.parseInt(spanId);
        }
    }

    public void addTag(final String key, final String value) {
        if (StringUtil.isNotEmpty(key) && StringUtil.isNotEmpty(value)) {
            lalTags.add(new String[]{key, value});
        }
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void init(final LogData logData, final Optional<Object> extraLog,
                     final ModuleManager moduleManager) {
        if (!INITIALIZED) {
            NAMING_CONTROL = moduleManager.find(CoreModule.NAME)
                                          .provider()
                                          .getService(NamingControl.class);
            final ConfigService configService = moduleManager.find(CoreModule.NAME)
                                                             .provider()
                                                             .getService(ConfigService.class);
            SEARCHABLE_TAG_KEYS = Arrays.asList(
                configService.getSearchableLogsTags().split(Const.COMMA));
            INITIALIZED = true;
        }
        this.logData = logData;
        // Only populate fields that were NOT already set by the LAL extractor.
        // The extractor runs before init(), so extractor values take priority.
        if (this.service == null) {
            this.service = logData.getService();
        }
        if (this.serviceInstance == null) {
            this.serviceInstance = logData.getServiceInstance();
        }
        if (this.endpoint == null) {
            this.endpoint = logData.getEndpoint();
        }
        if (this.layer == null) {
            this.layer = logData.getLayer();
        }
        final TraceContext tc = logData.getTraceContext();
        if (this.traceId == null) {
            this.traceId = tc.getTraceId();
        }
        if (this.segmentId == null) {
            this.segmentId = tc.getTraceSegmentId();
        }
        if (this.spanId < 0) {
            this.spanId = tc.getSpanId();
        }
        if (this.timestamp == 0) {
            this.timestamp = logData.getTimestamp();
        }
    }

    @Override
    @SneakyThrows
    public void complete(final SourceReceiver sourceReceiver) {
        final Log log = toLog();
        sourceReceiver.receive(log);
        addAutocompleteTags(sourceReceiver, log);
    }

    @SneakyThrows
    public Log toLog() {
        final Log log = new Log();
        log.setUniqueId(UUID.randomUUID().toString().replace("-", ""));
        log.setTimestamp(timestamp);
        log.setTimeBucket(TimeBucket.getRecordTimeBucket(timestamp));

        // service
        final String serviceName = NAMING_CONTROL.formatServiceName(service);
        final String serviceId = IDManager.ServiceID.buildId(serviceName, true);
        log.setServiceId(serviceId);
        // service instance
        if (StringUtil.isNotEmpty(serviceInstance)) {
            log.setServiceInstanceId(IDManager.ServiceInstanceID.buildId(
                serviceId,
                NAMING_CONTROL.formatInstanceName(serviceInstance)
            ));
        }
        // endpoint
        if (StringUtil.isNotEmpty(endpoint)) {
            final String endpointName = NAMING_CONTROL.formatEndpointName(serviceName, endpoint);
            log.setEndpointId(IDManager.EndpointID.buildId(serviceId, endpointName));
        }
        // trace
        if (StringUtil.isNotEmpty(traceId)) {
            log.setTraceId(traceId);
        }
        if (StringUtil.isNotEmpty(segmentId)) {
            log.setTraceSegmentId(segmentId);
            if (spanId >= 0) {
                log.setSpanId(spanId);
            }
        }
        // content
        final LogDataBody body = logData.getBody();
        if (body.hasText()) {
            log.setContentType(ContentType.TEXT);
            log.setContent(body.getText().getText());
        } else if (body.hasYaml()) {
            log.setContentType(ContentType.YAML);
            log.setContent(body.getYaml().getYaml());
        } else if (body.hasJson()) {
            log.setContentType(ContentType.JSON);
            log.setContent(body.getJson().getJson());
        }
        // raw tags from original LogData
        if (logData.getTags().getDataCount() > 0) {
            log.setTagsRawData(logData.getTags().toByteArray());
        }
        // searchable tags from LogData + LAL-added tags
        log.getTags().addAll(collectSearchableTags());

        return log;
    }

    private Collection<Tag> collectSearchableTags() {
        final HashSet<Tag> result = new HashSet<>();
        if (SEARCHABLE_TAG_KEYS != null) {
            // Tags from original LogData
            logData.getTags().getDataList().forEach(kv -> {
                if (SEARCHABLE_TAG_KEYS.contains(kv.getKey())) {
                    addSearchableTag(result, kv.getKey(), kv.getValue());
                }
            });
            // Tags added by LAL extractor
            for (final String[] kv : lalTags) {
                if (SEARCHABLE_TAG_KEYS.contains(kv[0])) {
                    addSearchableTag(result, kv[0], kv[1]);
                }
            }
        }
        return result;
    }

    private static void addSearchableTag(final HashSet<Tag> tags,
                                         final String key, final String value) {
        final Tag tag = new Tag(key, value);
        if (value.length() > Tag.TAG_LENGTH || tag.toString().length() > Tag.TAG_LENGTH) {
            if (log.isDebugEnabled()) {
                log.debug("Log tag : {} length > : {}, dropped", tag, Tag.TAG_LENGTH);
            }
            return;
        }
        tags.add(tag);
    }

    private void addAutocompleteTags(final SourceReceiver sourceReceiver,
                                     final Log log) {
        log.getTags().forEach(tag -> {
            final TagAutocomplete tagAutocomplete = new TagAutocomplete();
            tagAutocomplete.setTagKey(tag.getKey());
            tagAutocomplete.setTagValue(tag.getValue());
            tagAutocomplete.setTagType(TagType.LOG);
            tagAutocomplete.setTimeBucket(TimeBucket.getMinuteTimeBucket(log.getTimestamp()));
            sourceReceiver.receive(tagAutocomplete);
        });
    }
}
