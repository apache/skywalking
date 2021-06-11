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

package org.apache.skywalking.oap.server.analyzer.agent.kafka.provider.handler;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.utils.Bytes;
import org.apache.skywalking.apm.network.language.profile.v3.ThreadSnapshot;
import org.apache.skywalking.oap.server.analyzer.agent.kafka.module.KafkaFetcherConfig;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.worker.RecordStreamProcessor;
import org.apache.skywalking.oap.server.core.profile.ProfileThreadSnapshotRecord;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;
import org.apache.skywalking.oap.server.telemetry.api.CounterMetrics;
import org.apache.skywalking.oap.server.telemetry.api.HistogramMetrics;
import org.apache.skywalking.oap.server.telemetry.api.HistogramMetrics.Timer;
import org.apache.skywalking.oap.server.telemetry.api.MetricsCreator;
import org.apache.skywalking.oap.server.telemetry.api.MetricsTag.Keys;
import org.apache.skywalking.oap.server.telemetry.api.MetricsTag.Values;

/**
 * A handler deserializes the message of profiling snapshot and pushes it to downstream.
 */
@Slf4j
public class ProfileTaskHandler extends AbstractKafkaHandler {
    private final HistogramMetrics histogram;
    private final CounterMetrics errorCounter;

    public ProfileTaskHandler(ModuleManager manager, KafkaFetcherConfig config) {
        super(manager, config);
        MetricsCreator metricsCreator = manager.find(TelemetryModule.NAME)
                .provider()
                .getService(MetricsCreator.class);
        histogram = metricsCreator.createHistogramMetric(
                "profile_task_in_latency",
                "The process latency of profile task",
                new Keys("protocol"),
                new Values("kafka")
        );
        errorCounter = metricsCreator.createCounter(
                "profile_task_analysis_error_count",
                "The error number of profile task process",
                new Keys("protocol"),
                new Values("kafka")
        );
    }

    @Override
    public void handle(final ConsumerRecord<String, Bytes> record) {
        try (Timer ignored = histogram.createTimer()) {
            ThreadSnapshot snapshot = ThreadSnapshot.parseFrom(record.value().get());
            if (log.isDebugEnabled()) {
                log.debug(
                    "Fetched a thread snapshot[{}] from task[{}] reported",
                    snapshot.getTraceSegmentId(),
                    snapshot.getTaskId()
                );
            }

            final ProfileThreadSnapshotRecord snapshotRecord = new ProfileThreadSnapshotRecord();
            snapshotRecord.setTaskId(snapshot.getTaskId());
            snapshotRecord.setSegmentId(snapshot.getTraceSegmentId());
            snapshotRecord.setDumpTime(snapshot.getTime());
            snapshotRecord.setSequence(snapshot.getSequence());
            snapshotRecord.setStackBinary(snapshot.getStack().toByteArray());
            snapshotRecord.setTimeBucket(TimeBucket.getRecordTimeBucket(snapshot.getTime()));

            RecordStreamProcessor.getInstance().in(snapshotRecord);
        } catch (Exception e) {
            errorCounter.inc();
            log.error("handle record failed", e);
        }
    }

    @Override
    protected String getPlainTopic() {
        return config.getTopicNameOfProfiling();
    }
}
