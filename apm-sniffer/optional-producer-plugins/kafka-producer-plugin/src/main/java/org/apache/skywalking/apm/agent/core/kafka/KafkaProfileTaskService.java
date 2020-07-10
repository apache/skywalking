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

package org.apache.skywalking.apm.agent.core.kafka;

import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.utils.Bytes;
import org.apache.skywalking.apm.agent.core.boot.DefaultNamedThreadFactory;
import org.apache.skywalking.apm.agent.core.boot.OverrideImplementor;
import org.apache.skywalking.apm.agent.core.boot.ServiceManager;
import org.apache.skywalking.apm.agent.core.conf.Config;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.agent.core.profile.ProfileTaskChannelService;
import org.apache.skywalking.apm.agent.core.profile.TracingThreadSnapshot;
import org.apache.skywalking.apm.agent.core.remote.GRPCChannelManager;
import org.apache.skywalking.apm.network.language.profile.v3.ThreadSnapshot;
import org.apache.skywalking.apm.util.RunnableWithExceptionProtection;

@OverrideImplementor(ProfileTaskChannelService.class)
public class KafkaProfileTaskService extends ProfileTaskChannelService {
    private static final ILog logger = LogManager.getLogger(KafkaProfileTaskService.class);

    private String topic;
    private KafkaProducer<String, Bytes> producer;

    @Override
    public void prepare() {
        topic = Config.Collector.Kafka.TOPIC_PROFILING;
        ServiceManager.INSTANCE.findService(GRPCChannelManager.class).addChannelListener(this);
    }

    @Override
    public void boot() {
        if (Config.Profile.ACTIVE) {
            producer = ServiceManager.INSTANCE.findService(KafkaServiceManagementClient.class).getProducer();

            getTaskListFuture = Executors.newSingleThreadScheduledExecutor(
                new DefaultNamedThreadFactory("ProfileGetTaskService")
            ).scheduleWithFixedDelay(
                new RunnableWithExceptionProtection(
                    this,
                    t -> logger.error("Query profile task list failure.", t)
                ), 0, Config.Collector.GET_PROFILE_TASK_INTERVAL, TimeUnit.SECONDS
            );

            sendSnapshotFuture = Executors.newSingleThreadScheduledExecutor(
                new DefaultNamedThreadFactory("ProfileSendSnapshotService")
            ).scheduleWithFixedDelay(
                new RunnableWithExceptionProtection(
                    new SnapshotSender(),
                    t -> logger.error("Profile segment snapshot upload failure.", t)
                ), 0, 500, TimeUnit.MILLISECONDS
            );
        }
    }

    private class SnapshotSender implements Runnable {

        @Override
        public void run() {
            try {
                ArrayList<TracingThreadSnapshot> buffer = new ArrayList<>(
                    Config.Profile.SNAPSHOT_TRANSPORT_BUFFER_SIZE);
                snapshotQueue.drainTo(buffer);

                for (TracingThreadSnapshot tracingThreadSnapshot : buffer) {
                    final ThreadSnapshot snapshot = tracingThreadSnapshot.transform();
                    producer.send(new ProducerRecord<>(
                        topic,
                        snapshot.getTraceSegmentId(),
                        Bytes.wrap(snapshot.toByteArray())
                    ));
                }
            } catch (Throwable t) {
                logger.error(t, "Send profile segment snapshot to backend fail.");
            }

        }

    }
}
