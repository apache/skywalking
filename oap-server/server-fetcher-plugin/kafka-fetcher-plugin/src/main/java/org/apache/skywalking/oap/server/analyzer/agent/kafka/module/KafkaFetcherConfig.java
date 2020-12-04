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

package org.apache.skywalking.oap.server.analyzer.agent.kafka.module;

import lombok.Data;
import org.apache.skywalking.oap.server.library.module.ModuleConfig;

import java.util.Properties;

@Data
public class KafkaFetcherConfig extends ModuleConfig {

    /**
     * Kafka consumer config.
     */
    private Properties kafkaConsumerConfig = new Properties();

    /**
     *  <B>bootstrap.servers</B>: A list of host/port pairs to use for establishing the initial connection to the Kafka cluster.
     *  A list of host/port pairs to use for establishing the initial connection to the Kafka cluster.
     */
    private String bootstrapServers;

    /**
     * <B>group.id</B>: A unique string that identifies the consumer group this consumer belongs to.
     */
    private String groupId = "skywalking-consumer";

    /**
     * Which PartitionId(s) of the topics assign to the OAP server. If more than one, is separated by commas.
     */
    private String consumePartitions = "";

    /**
     * isSharding was true when OAP Server in cluster.
     */
    private boolean isSharding = false;

    /**
     * If true, create the Kafka topic when it does not exist.
     */
    private boolean createTopicIfNotExist = true;

    /**
     * The number of partitions for the topic being created.
     */
    private int partitions = 3;

    /**
     * The replication factor for each partition in the topic being created.
     */
    private int replicationFactor = 2;

    private boolean enableMeterSystem = false;

    private String configPath = "meter-analyzer-config";

    private String topicNameOfMetrics = "skywalking-metrics";

    private String topicNameOfProfiling = "skywalking-profilings";

    private String topicNameOfTracingSegments = "skywalking-segments";

    private String topicNameOfManagements = "skywalking-managements";

    private String topicNameOfMeters = "skywalking-meters";

    private int kafkaHandlerThreadPoolSize;

    private int kafkaHandlerThreadPoolQueueSize;
    
    private String mm2SourceAlias = "";

    private String mm2SourceSeparator = "";
    
}
