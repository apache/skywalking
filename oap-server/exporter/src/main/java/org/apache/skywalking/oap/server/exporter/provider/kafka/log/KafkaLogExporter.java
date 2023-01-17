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

package org.apache.skywalking.oap.server.exporter.provider.kafka.log;

import com.google.protobuf.InvalidProtocolBufferException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.utils.Bytes;
import org.apache.skywalking.apm.network.logging.v3.JSONLog;
import org.apache.skywalking.apm.network.logging.v3.LogData;
import org.apache.skywalking.apm.network.logging.v3.LogDataBody;
import org.apache.skywalking.apm.network.logging.v3.LogTags;
import org.apache.skywalking.apm.network.logging.v3.TextLog;
import org.apache.skywalking.apm.network.logging.v3.TraceContext;
import org.apache.skywalking.apm.network.logging.v3.YAMLLog;
import org.apache.skywalking.oap.server.core.UnexpectedException;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.manual.log.LogRecord;
import org.apache.skywalking.oap.server.core.exporter.LogExportService;
import org.apache.skywalking.oap.server.core.query.type.ContentType;
import org.apache.skywalking.oap.server.exporter.provider.ExporterSetting;
import org.apache.skywalking.oap.server.exporter.provider.kafka.KafkaExportProducer;
import org.apache.skywalking.oap.server.library.datacarrier.DataCarrier;
import org.apache.skywalking.oap.server.library.datacarrier.buffer.BufferStrategy;
import org.apache.skywalking.oap.server.library.datacarrier.consumer.IConsumer;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;
import org.apache.skywalking.oap.server.telemetry.api.CounterMetrics;
import org.apache.skywalking.oap.server.telemetry.api.MetricsCreator;
import org.apache.skywalking.oap.server.telemetry.api.MetricsTag;

@Slf4j
public class KafkaLogExporter extends KafkaExportProducer implements LogExportService, IConsumer<LogRecord> {
    private DataCarrier<LogRecord> exportBuffer;
    private CounterMetrics successCounter;
    private CounterMetrics errorCounter;
    private final ModuleManager moduleManager;

    public KafkaLogExporter(ModuleManager manager, ExporterSetting setting) {
        super(setting);
        this.moduleManager = manager;
    }

    @Override
    public void start() {
        super.getProducer();
        exportBuffer = new DataCarrier<>(
            "KafkaLogExporter", "KafkaLogExporter", setting.getBufferChannelNum(), setting.getBufferChannelSize(),
            BufferStrategy.IF_POSSIBLE
        );
        exportBuffer.consume(this, 1, 200);
        MetricsCreator metricsCreator = moduleManager.find(TelemetryModule.NAME)
                                                     .provider()
                                                     .getService(MetricsCreator.class);
        successCounter = metricsCreator.createCounter(
            "kafka_exporter_log_success_count", "The success number of log exported by kafka exporter.",
            new MetricsTag.Keys("protocol"),
            new MetricsTag.Values("kafka")
        );
        errorCounter = metricsCreator.createCounter(
            "kafka_exporter_log_error_count", "The error number of log exported by kafka exporter",
            new MetricsTag.Keys("protocol"), new MetricsTag.Values("kafka")
        );
    }

    @Override
    public void export(final LogRecord logRecord) {
        if (logRecord != null) {
            exportBuffer.produce(logRecord);
        }
    }

    @Override
    public boolean isEnabled() {
        return setting.isEnableKafkaLog();
    }

    @Override
    public void consume(final List<LogRecord> data) {
        for (LogRecord logRecord : data) {
            if (logRecord != null) {
                try {
                    LogData logData = transLogData(logRecord);
                    ProducerRecord<String, Bytes> record = new ProducerRecord<>(
                        setting.getKafkaTopicLog(),
                        logRecord.id().build(),
                        Bytes.wrap(logData.toByteArray())
                    );
                    super.getProducer().send(record, (metadata, ex) -> {
                        if (ex != null) {
                            errorCounter.inc();
                            log.error("Failed to export Log.", ex);
                        } else {
                            successCounter.inc();
                        }
                    });
                } catch (InvalidProtocolBufferException e) {
                    throw new UnexpectedException(
                        "Failed to parse Log tags from LogRecord, id: " + logRecord.id() + ".", e);
                }
            }
        }
    }

    @Override
    public void onError(final List<LogRecord> data, final Throwable t) {

    }

    private LogData transLogData(LogRecord logRecord) throws InvalidProtocolBufferException {
        LogData.Builder builder = LogData.newBuilder();
        LogDataBody.Builder bodyBuilder = LogDataBody.newBuilder();
        switch (ContentType.instanceOf(logRecord.getContentType())) {
            case JSON:
                bodyBuilder.setType(ContentType.JSON.name());
                bodyBuilder.setJson(JSONLog.newBuilder().setJson(logRecord.getContent().getText()));
                break;
            case YAML:
                bodyBuilder.setType(ContentType.YAML.name());
                bodyBuilder.setYaml(YAMLLog.newBuilder().setYaml(logRecord.getContent().getText()));
                break;
            case TEXT:
                bodyBuilder.setType(ContentType.TEXT.name());
                bodyBuilder.setText(TextLog.newBuilder().setText(logRecord.getContent().getText()));
                break;
            case NONE:
                bodyBuilder.setType(ContentType.NONE.name());
                break;
            default:
                throw new UnexpectedException(
                    "Failed to parse Log ContentType value: " + logRecord.getContentType() + " from LogRecord, id: " + logRecord.id() + ".");
        }
        builder.setBody(bodyBuilder);

        builder.setTimestamp(logRecord.getTimestamp());
        builder.setService(IDManager.ServiceID.analysisId(logRecord.getServiceId()).getName());
        if (StringUtil.isNotEmpty(logRecord.getServiceInstanceId())) {
            builder.setServiceInstance(
                IDManager.ServiceInstanceID.analysisId(logRecord.getServiceInstanceId()).getName());
        }
        if (StringUtil.isNotEmpty(logRecord.getEndpointId())) {
            builder.setEndpoint(
                IDManager.EndpointID.analysisId(logRecord.getEndpointId()).getEndpointName());
        }

        TraceContext.Builder contextBuilder = TraceContext.newBuilder();
        if (StringUtil.isNotEmpty(logRecord.getTraceSegmentId())) {
            contextBuilder.setTraceSegmentId(logRecord.getTraceSegmentId());
            contextBuilder.setSpanId(logRecord.getSpanId());
        }
        if (StringUtil.isNotEmpty(logRecord.getTraceId())) {
            contextBuilder.setTraceId(logRecord.getTraceId());
        }
        builder.setTraceContext(contextBuilder);
        if (logRecord.getTagsRawData() != null) {
            builder.setTags(LogTags.parseFrom(logRecord.getTagsRawData()));
        }
        return builder.build();
    }
}
