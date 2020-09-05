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

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.utils.Bytes;
import org.apache.skywalking.apm.agent.core.boot.BootService;
import org.apache.skywalking.apm.agent.core.boot.OverrideImplementor;
import org.apache.skywalking.apm.agent.core.boot.ServiceManager;
import org.apache.skywalking.apm.agent.core.context.TracingContext;
import org.apache.skywalking.apm.agent.core.context.TracingContextListener;
import org.apache.skywalking.apm.agent.core.context.trace.TraceSegment;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.agent.core.remote.TraceSegmentServiceClient;
import org.apache.skywalking.apm.network.language.agent.v3.SegmentObject;

/**
 *  A tracing segment data reporter.
 */
@OverrideImplementor(TraceSegmentServiceClient.class)
public class KafkaTraceSegmentServiceClient implements BootService, TracingContextListener {
    private static final ILog LOGGER = LogManager.getLogger(KafkaTraceSegmentServiceClient.class);

    private String topic;
    private KafkaProducer<String, Bytes> producer;

    @Override
    public void prepare() {
        topic = KafkaReporterPluginConfig.Plugin.Kafka.TOPIC_SEGMENT;
    }

    @Override
    public void boot() {
        producer = ServiceManager.INSTANCE.findService(KafkaProducerManager.class).getProducer();
    }

    @Override
    public void onComplete() {
        TracingContext.ListenerManager.add(this);
    }

    @Override
    public void shutdown() {
        TracingContext.ListenerManager.remove(this);
    }

    @Override
    public void afterFinished(final TraceSegment traceSegment) {
        if (LOGGER.isDebugEnable()) {
            LOGGER.debug("Trace segment reporting, traceId: {}", traceSegment.getTraceSegmentId());
        }

        if (traceSegment.isIgnore()) {
            LOGGER.debug("Trace[TraceId={}] is ignored.", traceSegment.getTraceSegmentId());
            return;
        }
        SegmentObject upstreamSegment = traceSegment.transform();
        ProducerRecord<String, Bytes> record = new ProducerRecord<>(
            topic,
            upstreamSegment.getTraceSegmentId(),
            Bytes.wrap(upstreamSegment.toByteArray())
        );
        producer.send(record);
    }

}