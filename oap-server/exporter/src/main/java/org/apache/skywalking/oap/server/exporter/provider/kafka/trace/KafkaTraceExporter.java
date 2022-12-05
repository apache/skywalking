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

package org.apache.skywalking.oap.server.exporter.provider.kafka.trace;

import com.google.protobuf.InvalidProtocolBufferException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.utils.Bytes;
import org.apache.skywalking.apm.network.language.agent.v3.SegmentObject;
import org.apache.skywalking.oap.server.core.UnexpectedException;
import org.apache.skywalking.oap.server.core.analysis.manual.segment.SegmentRecord;
import org.apache.skywalking.oap.server.core.exporter.TraceExportService;
import org.apache.skywalking.oap.server.exporter.provider.ExporterSetting;
import org.apache.skywalking.oap.server.exporter.provider.kafka.KafkaExportProducer;
import org.apache.skywalking.oap.server.library.datacarrier.DataCarrier;
import org.apache.skywalking.oap.server.library.datacarrier.buffer.BufferStrategy;
import org.apache.skywalking.oap.server.library.datacarrier.consumer.IConsumer;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;
import org.apache.skywalking.oap.server.telemetry.api.CounterMetrics;
import org.apache.skywalking.oap.server.telemetry.api.MetricsCreator;
import org.apache.skywalking.oap.server.telemetry.api.MetricsTag;

@Slf4j
public class KafkaTraceExporter extends KafkaExportProducer implements TraceExportService, IConsumer<SegmentRecord> {
    private DataCarrier<SegmentRecord> exportBuffer;
    private CounterMetrics successCounter;
    private CounterMetrics errorCounter;
    private final ModuleManager moduleManager;

    public KafkaTraceExporter(ModuleManager manager, ExporterSetting setting) {
        super(setting);
        this.moduleManager = manager;
    }

    @Override
    public void start() {
        super.getProducer();
        exportBuffer = new DataCarrier<>(
            "KafkaTraceExporter", "KafkaTraceExporter", setting.getBufferChannelNum(), setting.getBufferChannelSize(),
            BufferStrategy.IF_POSSIBLE
        );
        exportBuffer.consume(this, 1, 200);
        MetricsCreator metricsCreator = moduleManager.find(TelemetryModule.NAME)
                                                     .provider()
                                                     .getService(MetricsCreator.class);
        successCounter = metricsCreator.createCounter(
            "kafka_exporter_trace_success_count", "The success number of traces exported by kafka exporter.",
            new MetricsTag.Keys("protocol"),
            new MetricsTag.Values("kafka")
        );
        errorCounter = metricsCreator.createCounter(
            "kafka_exporter_trace_error_count", "The error number of traces exported by kafka exporter",
            new MetricsTag.Keys("protocol"), new MetricsTag.Values("kafka")
        );
    }

    public void export(SegmentRecord segmentRecord) {
        if (segmentRecord != null) {
            exportBuffer.produce(segmentRecord);
        }

    }

    @Override
    public boolean isEnabled() {
        return setting.isEnableKafkaTrace();
    }

    @Override
    public void consume(final List<SegmentRecord> data) {
        for (SegmentRecord segmentRecord : data) {
            if (segmentRecord != null) {
                try {
                    SegmentObject segmentObject = SegmentObject.parseFrom(segmentRecord.getDataBinary());
                    ProducerRecord<String, Bytes> record = new ProducerRecord<>(
                        setting.getKafkaTopicTrace(),
                        segmentObject.getTraceSegmentId(),
                        Bytes.wrap(segmentObject.toByteArray())
                    );
                    super.getProducer().send(record, (metadata, ex) -> {
                        if (ex != null) {
                            errorCounter.inc();
                            log.error("Failed to export Trace.", ex);
                        } else {
                            successCounter.inc();
                        }
                    });
                } catch (InvalidProtocolBufferException e) {
                    throw new UnexpectedException(
                        "Failed to parse SegmentObject from SegmentRecord, id: " + segmentRecord.getSegmentId() + ".", e
                    );
                }
            }
        }
    }

    @Override
    public void onError(final List<SegmentRecord> data, final Throwable t) {

    }
}
