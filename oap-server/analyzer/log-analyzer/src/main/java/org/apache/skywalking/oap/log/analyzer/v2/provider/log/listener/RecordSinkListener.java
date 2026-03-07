/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.skywalking.oap.log.analyzer.v2.provider.log.listener;

import com.google.protobuf.Message;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.SneakyThrows;
import org.apache.skywalking.apm.network.logging.v3.LogData;
import org.apache.skywalking.apm.network.logging.v3.LogDataBody;
import org.apache.skywalking.apm.network.logging.v3.TraceContext;

import org.apache.skywalking.oap.log.analyzer.v2.dsl.ExecutionContext;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.TagType;
import org.apache.skywalking.oap.server.core.source.AbstractLog;
import org.apache.skywalking.oap.server.core.source.LALOutputBuilder;
import org.apache.skywalking.oap.server.core.source.TagAutocomplete;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.oap.log.analyzer.v2.provider.LogAnalyzerModuleConfig;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.Tag;
import org.apache.skywalking.oap.server.core.config.ConfigService;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.query.type.ContentType;
import org.apache.skywalking.oap.server.core.source.Log;
import org.apache.skywalking.oap.server.core.source.SourceReceiver;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.skywalking.oap.server.library.util.ProtoBufJsonUtils.toJSON;

/**
 * RecordSinkListener forwards the log data to the persistence layer with the query required conditions.
 *
 * <p>Supports two output paths based on the {@code outputType}:
 * <ul>
 *   <li>{@link AbstractLog} subclass — standard log record path: common fields populated from
 *       LogData, subclass-specific fields via {@link AbstractLog#prepare} and output field assignments</li>
 *   <li>{@link LALOutputBuilder} implementation — builder path: {@code init()} pre-populates from
 *       LogData, output fields applied via reflection, {@code complete()} dispatches final source(s)</li>
 * </ul>
 */
public class RecordSinkListener implements LogSinkListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(RecordSinkListener.class);
    private final SourceReceiver sourceReceiver;
    private final NamingControl namingControl;
    private final List<String> searchableTagKeys;
    private final Class<?> outputType;
    private final boolean isBuilderMode;

    @Getter
    private AbstractLog log;
    private LALOutputBuilder builder;

    RecordSinkListener(final SourceReceiver sourceReceiver,
                       final NamingControl namingControl,
                       final List<String> searchableTagKeys,
                       final Class<?> outputType) {
        this.sourceReceiver = sourceReceiver;
        this.namingControl = namingControl;
        this.searchableTagKeys = searchableTagKeys;
        this.outputType = outputType;
        this.isBuilderMode = LALOutputBuilder.class.isAssignableFrom(outputType);
    }

    @Override
    public void build() {
        if (isBuilderMode) {
            if (builder != null) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("RecordSinkListener invoking builder.complete() on {}",
                        builder.getClass().getSimpleName());
                }
                builder.complete(sourceReceiver);
            } else {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("RecordSinkListener builder is null, skipping build");
                }
            }
        } else {
            sourceReceiver.receive(log);
            addAutocompleteTags();
        }
    }

    @Override
    @SneakyThrows
    public LogSinkListener parse(final LogData.Builder logData,
                                     final Message extraLog) {
        if (isBuilderMode) {
            return parseBuilder(logData);
        }
        return parseAbstractLog(logData, extraLog);
    }

    @SneakyThrows
    private LogSinkListener parseBuilder(final LogData.Builder logData) {
        builder = (LALOutputBuilder) outputType.getDeclaredConstructor().newInstance();
        builder.init(logData.build(), namingControl);
        return this;
    }

    @SneakyThrows
    private LogSinkListener parseAbstractLog(final LogData.Builder logData,
                                              final Message extraLog) {
        log = createOutputSource();
        LogDataBody body = logData.getBody();
        log.setUniqueId(UUID.randomUUID().toString().replace("-", ""));
        // timestamp
        log.setTimestamp(logData.getTimestamp());
        log.setTimeBucket(TimeBucket.getRecordTimeBucket(logData.getTimestamp()));

        // service
        String serviceName = namingControl.formatServiceName(logData.getService());
        String serviceId = IDManager.ServiceID.buildId(serviceName, true);
        log.setServiceId(serviceId);
        // service instance
        if (StringUtil.isNotEmpty(logData.getServiceInstance())) {
            log.setServiceInstanceId(IDManager.ServiceInstanceID.buildId(
                serviceId,
                namingControl.formatInstanceName(logData.getServiceInstance())
            ));
        }
        // endpoint
        if (StringUtil.isNotEmpty(logData.getEndpoint())) {
            String endpointName = namingControl.formatEndpointName(serviceName, logData.getEndpoint());
            log.setEndpointId(IDManager.EndpointID.buildId(serviceId, endpointName));
        }
        // trace
        TraceContext traceContext = logData.getTraceContext();
        if (StringUtil.isNotEmpty(traceContext.getTraceId())) {
            log.setTraceId(traceContext.getTraceId());
        }
        if (StringUtil.isNotEmpty(traceContext.getTraceSegmentId())) {
            log.setTraceSegmentId(traceContext.getTraceSegmentId());
            log.setSpanId(traceContext.getSpanId());
        }
        // content
        if (body.hasText()) {
            log.setContentType(ContentType.TEXT);
            log.setContent(body.getText().getText());
        } else if (body.hasYaml()) {
            log.setContentType(ContentType.YAML);
            log.setContent(body.getYaml().getYaml());
        } else if (body.hasJson()) {
            log.setContentType(ContentType.JSON);
            log.setContent(body.getJson().getJson());
        } else if (extraLog != null) {
            log.setContentType(ContentType.JSON);
            log.setContent(toJSON(extraLog));
        }
        if (logData.getTags().getDataCount() > 0) {
            log.setTagsRawData(logData.getTags().toByteArray());
        }
        log.getTags().addAll(appendSearchableTags(logData));

        // Let the output source class populate its custom fields
        log.prepare(logData, namingControl);

        return this;
    }

    @Override
    @SneakyThrows
    public LogSinkListener parse(final LogData.Builder logData,
                                 final Message extraLog,
                                 final ExecutionContext ctx) {
        parse(logData, extraLog);
        if (ctx != null) {
            final Object target = isBuilderMode ? builder : log;
            applyOutputFields(ctx.outputFields(), target);
        }
        return this;
    }

    private void applyOutputFields(final Map<String, Object> outputFields, final Object target) {
        if (outputFields == null || outputFields.isEmpty() || target == null) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("No output fields to apply: fields={}, target={}",
                    outputFields, target != null ? target.getClass().getSimpleName() : "null");
            }
            return;
        }
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Applying {} output fields to {}: {}",
                outputFields.size(), target.getClass().getSimpleName(), outputFields.keySet());
        }
        for (final Map.Entry<String, Object> entry : outputFields.entrySet()) {
            final String fieldName = entry.getKey();
            final Object value = entry.getValue();
            if (value == null) {
                continue;
            }
            final String setterName = "set" + Character.toUpperCase(fieldName.charAt(0))
                + fieldName.substring(1);
            try {
                java.lang.reflect.Method setter = findSetter(target.getClass(), setterName);
                if (setter != null) {
                    setter.setAccessible(true);
                    setter.invoke(target, convertValue(value, setter.getParameterTypes()[0]));
                }
            } catch (Exception e) {
                LOGGER.warn("Failed to set output field '{}' on {}: {}",
                    fieldName, target.getClass().getSimpleName(), e.getMessage());
            }
        }
    }

    static java.lang.reflect.Method findSetter(final Class<?> clazz, final String name) {
        for (Class<?> c = clazz; c != null; c = c.getSuperclass()) {
            for (final java.lang.reflect.Method m : c.getDeclaredMethods()) {
                if (m.getName().equals(name) && m.getParameterCount() == 1) {
                    return m;
                }
            }
        }
        return null;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static Object convertValue(final Object value, final Class<?> targetType) {
        if (targetType.isInstance(value)) {
            return value;
        }
        final String str = String.valueOf(value);
        if (targetType == long.class || targetType == Long.class) {
            return Long.parseLong(str);
        }
        if (targetType == int.class || targetType == Integer.class) {
            return Integer.parseInt(str);
        }
        if (targetType == double.class || targetType == Double.class) {
            return Double.parseDouble(str);
        }
        if (targetType == boolean.class || targetType == Boolean.class) {
            return Boolean.parseBoolean(str);
        }
        if (targetType.isEnum()) {
            return Enum.valueOf((Class<Enum>) targetType, str);
        }
        return str;
    }

    @SuppressWarnings("unchecked")
    private AbstractLog createOutputSource() {
        try {
            return ((Class<? extends AbstractLog>) outputType).getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new IllegalStateException(
                "Failed to create output source instance: " + outputType.getName(), e);
        }
    }

    private Collection<Tag> appendSearchableTags(LogData.Builder logData) {
        HashSet<Tag> logTags = new HashSet<>();
        logData.getTags().getDataList().forEach(tag -> {
            if (searchableTagKeys.contains(tag.getKey())) {
                final Tag logTag = new Tag(tag.getKey(), tag.getValue());
                if (tag.getValue().length()  > Tag.TAG_LENGTH || logTag.toString().length() > Tag.TAG_LENGTH) {
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("Log tag : {} length > : {}, dropped", logTag, Tag.TAG_LENGTH);
                    }
                    return;
                }
                logTags.add(logTag);
            }
        });
        return logTags;
    }

    private void addAutocompleteTags() {
        log.getTags().forEach(tag -> {
            TagAutocomplete tagAutocomplete = new TagAutocomplete();
            tagAutocomplete.setTagKey(tag.getKey());
            tagAutocomplete.setTagValue(tag.getValue());
            tagAutocomplete.setTagType(TagType.LOG);
            tagAutocomplete.setTimeBucket(TimeBucket.getMinuteTimeBucket(log.getTimestamp()));
            sourceReceiver.receive(tagAutocomplete);
        });
    }

    public static class Factory implements LogSinkListenerFactory {
        private final SourceReceiver sourceReceiver;
        private final NamingControl namingControl;
        private final List<String> searchableTagKeys;
        private final Class<?> outputType;

        public Factory(ModuleManager moduleManager, LogAnalyzerModuleConfig moduleConfig) {
            this(moduleManager, moduleConfig, Log.class);
        }

        public Factory(ModuleManager moduleManager,
                       LogAnalyzerModuleConfig moduleConfig,
                       Class<?> outputType) {
            this.sourceReceiver = moduleManager.find(CoreModule.NAME)
                                               .provider()
                                               .getService(SourceReceiver.class);
            this.namingControl = moduleManager.find(CoreModule.NAME)
                                              .provider()
                                              .getService(NamingControl.class);
            ConfigService configService = moduleManager.find(CoreModule.NAME)
                                                       .provider()
                                                       .getService(ConfigService.class);
            this.searchableTagKeys = Arrays.asList(configService.getSearchableLogsTags().split(Const.COMMA));
            this.outputType = outputType;
        }

        @Override
        public RecordSinkListener create() {
            return new RecordSinkListener(sourceReceiver, namingControl, searchableTagKeys, outputType);
        }
    }
}
