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

/**
 * A handler deserializes the message of profiling snapshot and pushes it to downstream.
 */
@Slf4j
public class ProfileTaskHandler implements KafkaHandler {

    private final KafkaFetcherConfig config;

    public ProfileTaskHandler(ModuleManager manager, KafkaFetcherConfig config) {
        this.config = config;
    }

    @Override
    public void handle(final ConsumerRecord<String, Bytes> record) {
        try {
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
            log.error("handle record failed", e);
        }
    }

    @Override
    public String getTopic() {
        return config.getMm2SourceAlias() + config.getMm2SourceSeparator() + config.getTopicNameOfProfiling();
    }

    @Override
    public String getConsumePartitions() {
        return config.getConsumePartitions();
    }
}
