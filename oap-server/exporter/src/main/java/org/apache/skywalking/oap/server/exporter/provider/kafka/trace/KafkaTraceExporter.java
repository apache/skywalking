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
import org.apache.skywalking.apm.network.language.agent.v3.SpanObject;
import org.apache.skywalking.oap.server.core.UnexpectedException;
import org.apache.skywalking.oap.server.core.analysis.manual.segment.SegmentRecord;
import org.apache.skywalking.oap.server.core.exporter.TraceExportService;
import org.apache.skywalking.oap.server.exporter.provider.ExporterSetting;
import org.apache.skywalking.oap.server.exporter.provider.kafka.KafkaExportProducer;
import org.apache.skywalking.oap.server.library.batchqueue.BatchQueue;
import org.apache.skywalking.oap.server.library.batchqueue.BatchQueueConfig;
import org.apache.skywalking.oap.server.library.batchqueue.BatchQueueManager;
import org.apache.skywalking.oap.server.library.batchqueue.BufferStrategy;
import org.apache.skywalking.oap.server.library.batchqueue.ThreadPolicy;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;
import org.apache.skywalking.oap.server.telemetry.api.CounterMetrics;
import org.apache.skywalking.oap.server.telemetry.api.MetricsCreator;
import org.apache.skywalking.oap.server.telemetry.api.MetricsTag;

@Slf4j
public class KafkaTraceExporter extends KafkaExportProducer implements TraceExportService {
    private BatchQueue<SegmentRecord> queue;
    private CounterMetrics successCounter;
    private CounterMetrics errorCounter;
    private final ModuleManager moduleManager;

    public KafkaTraceExporter(ModuleManager manager, ExporterSetting setting) {
        super(setting);
        this.moduleManager = manager;
    }

    public void start() {
        super.getProducer();
        this.queue = BatchQueueManager.create(
            "EXPORTER_KAFKA_TRACE",
            BatchQueueConfig.<SegmentRecord>builder()
                .threads(ThreadPolicy.fixed(1))
                .bufferSize(setting.getBufferSize())
                .strategy(BufferStrategy.IF_POSSIBLE)
                .consumer(this::consumeSegmentRecords)
                .maxIdleMs(200)
                .build()
        );
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
            queue.produce(segmentRecord);
        }
    }

    @Override
    public boolean isEnabled() {
        return setting.isEnableKafkaTrace();
    }

    private void consumeSegmentRecords(final List<SegmentRecord> data) {
        for (SegmentRecord segmentRecord : data) {
            if (segmentRecord != null) {
                try {
                    SegmentObject segmentObject = SegmentObject.parseFrom(segmentRecord.getDataBinary());
                    if (setting.isExportErrorStatusTraceOnly() && !isError(segmentObject)) {
                        continue;
                    }
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

    private boolean isError(SegmentObject segmentObject) {
        for (SpanObject spanObject : segmentObject.getSpansList()) {
            if (spanObject.getIsError()) {
                return true;
            }
        }
        return false;
    }
}
