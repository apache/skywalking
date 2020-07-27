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

import com.google.common.collect.Lists;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.utils.Bytes;
import org.apache.skywalking.apm.network.language.agent.v3.MeterData;
import org.apache.skywalking.apm.network.language.agent.v3.MeterReportServiceGrpc;
import org.apache.skywalking.oap.server.analyzer.agent.kafka.module.KafkaFetcherConfig;
import org.apache.skywalking.oap.server.analyzer.provider.meter.process.MeterProcessContext;
import org.apache.skywalking.oap.server.analyzer.provider.meter.process.MeterProcessor;

@Slf4j
public class MeterServiceHandler extends MeterReportServiceGrpc.MeterReportServiceImplBase implements KafkaHandler {
    private KafkaFetcherConfig config;
    private MeterProcessContext processContext;

    public MeterServiceHandler(MeterProcessContext processContext, KafkaFetcherConfig config) {
        this.config = config;
        this.processContext = processContext;
    }

    @Override
    public void handle(final ConsumerRecord<String, Bytes> record) {
        try {
            MeterData meterData = MeterData.parseFrom(record.value().get());

            MeterProcessor processor = processContext.createProcessor();
            processor.read(meterData);
            processor.process();

        } catch (InvalidProtocolBufferException e) {
            log.error("", e);
        }
    }

    @Override
    public List<TopicPartition> getTopicPartitions() {
        String[] partitions = config.getConsumePartitions().split("\\s*,\\s*");
        List<TopicPartition> topicPartitions = Lists.newArrayList();
        for (final String partition : partitions) {
            topicPartitions.add(new TopicPartition(getTopic(), Integer.parseInt(partition)));
        }
        return topicPartitions;
    }

    @Override
    public String getTopic() {
        return config.getTopicNameOfMeters();
    }
}
