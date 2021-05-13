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
import java.util.Collections;
import java.util.List;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.utils.Bytes;
import org.apache.skywalking.apm.util.StringUtil;

/**
 * A Handler for dealing Message reported by agent. It is binding to a topic of Kafka, and deserialize.
 */
public interface KafkaHandler {

    /**
     * Which partition(s) of the topic is handled in cluster mode. Currently, we have to specify the handler working for
     * partition(s).
     */
    default List<TopicPartition> getTopicPartitions() {
        if (StringUtil.isEmpty(getConsumePartitions())) {
            return Collections.EMPTY_LIST;
        }

        List<TopicPartition> topicPartitions = Lists.newArrayList();
        for (final String partition : getConsumePartitions().trim().split("\\s*,\\s*")) {
            topicPartitions.add(new TopicPartition(getTopic(), Integer.parseInt(partition)));
        }
        return topicPartitions;
    }

    /**
     *
     */
    String getConsumePartitions();

    /**
     * A topic of Kafka is handled.
     */
    String getTopic();

    /**
     * Deserialize and push it to downstream.
     */
    void handle(ConsumerRecord<String, Bytes> record);

}
