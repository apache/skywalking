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

package org.apache.skywalking.oap.server.analyzer.agent.pulsar.provider.handler;

import lombok.extern.slf4j.Slf4j;
import org.apache.pulsar.client.api.Message;
import org.apache.skywalking.apm.network.language.profile.v3.ThreadSnapshot;
import org.apache.skywalking.oap.server.analyzer.agent.pulsar.module.PulsarFetcherConfig;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.worker.RecordStreamProcessor;
import org.apache.skywalking.oap.server.core.profile.ProfileThreadSnapshotRecord;
import org.apache.skywalking.oap.server.library.module.ModuleManager;

/**
 * A handler deserializes the message of profiling snapshot and pushes it to downstream.
 */
@Slf4j
public class ProfileTaskHandler implements PulsarHandler {

    private final PulsarFetcherConfig config;

    public ProfileTaskHandler(ModuleManager manager, PulsarFetcherConfig config) {
        this.config = config;
    }

    @Override
    public void handle(final Message msg) {
        try {
            ThreadSnapshot snapshot = ThreadSnapshot.parseFrom(msg.getData());
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
        return "persistent://" + config.getTenant() + "/" + config.getNamespace() + "/" + config
                .getTopicNameOfProfiling();
    }

}
