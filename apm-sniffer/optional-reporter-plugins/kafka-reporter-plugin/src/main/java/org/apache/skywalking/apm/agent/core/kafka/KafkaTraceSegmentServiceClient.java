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

import java.util.List;
import java.util.Objects;
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
import org.apache.skywalking.apm.commons.datacarrier.DataCarrier;
import org.apache.skywalking.apm.commons.datacarrier.buffer.BufferStrategy;
import org.apache.skywalking.apm.commons.datacarrier.consumer.IConsumer;
import org.apache.skywalking.apm.network.language.agent.v3.SegmentObject;

import static org.apache.skywalking.apm.agent.core.conf.Config.Buffer.BUFFER_SIZE;
import static org.apache.skywalking.apm.agent.core.conf.Config.Buffer.CHANNEL_SIZE;

/**
 * A tracing segment data reporter.
 */
@OverrideImplementor(TraceSegmentServiceClient.class)
public class KafkaTraceSegmentServiceClient implements BootService, IConsumer<TraceSegment>, TracingContextListener, KafkaConnectionStatusListener {
    private static final ILog LOGGER = LogManager.getLogger(KafkaTraceSegmentServiceClient.class);

    private String topic;
    private KafkaProducer<String, Bytes> producer;

    private volatile DataCarrier<TraceSegment> carrier;

    @Override
    public void prepare() {
        KafkaProducerManager producerManager = ServiceManager.INSTANCE.findService(KafkaProducerManager.class);
        producerManager.addListener(this);
        topic = producerManager.formatTopicNameThenRegister(KafkaReporterPluginConfig.Plugin.Kafka.TOPIC_SEGMENT);
    }

    @Override
    public void boot() {
        carrier = new DataCarrier<>(CHANNEL_SIZE, BUFFER_SIZE, BufferStrategy.IF_POSSIBLE);
        carrier.consume(this, 1);
    }

    @Override
    public void onComplete() {
        TracingContext.ListenerManager.add(this);
    }

    @Override
    public void shutdown() {
        TracingContext.ListenerManager.remove(this);
        carrier.shutdownConsumers();
    }

    @Override
    public void init() {

    }

    @Override
    public void consume(final List<TraceSegment> data) {
        if (producer == null) {
            return;
        }
        data.forEach(traceSegment -> {
            SegmentObject upstreamSegment = traceSegment.transform();
            ProducerRecord<String, Bytes> record = new ProducerRecord<>(
                topic,
                upstreamSegment.getTraceSegmentId(),
                Bytes.wrap(upstreamSegment.toByteArray())
            );
            producer.send(record, (m, e) -> {
                if (Objects.nonNull(e)) {
                    LOGGER.error("Failed to report TraceSegment.", e);
                }
            });
        });
    }

    @Override
    public void onError(final List<TraceSegment> data, final Throwable t) {
        LOGGER.error(t, "Try to send {} trace segments to collector, with unexpected exception.", data.size());
    }

    @Override
    public void onExit() {

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
        carrier.produce(traceSegment);
    }

    @Override
    public void onStatusChanged(KafkaConnectionStatus status) {
        if (status == KafkaConnectionStatus.CONNECTED) {
            producer = ServiceManager.INSTANCE.findService(KafkaProducerManager.class).getProducer();
        }
    }
}