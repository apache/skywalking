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

package org.apache.skywalking.oap.server.receiver.zipkin;

import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.library.module.ModuleConfig;

@Setter
@Getter
public class ZipkinReceiverConfig extends ModuleConfig {
    // HTTP collector config
    private boolean enableHttpCollector = true;
    private String restHost;
    private int restPort;
    private String restContextPath;
    private int restMaxThreads = 200;
    private long restIdleTimeOut = 30000;
    private int restAcceptQueueSize = 0;
    private String searchableTracesTags = DEFAULT_SEARCHABLE_TAG_KEYS;
    private int sampleRate = 10000;

    private static final String DEFAULT_SEARCHABLE_TAG_KEYS = String.join(
        Const.COMMA,
        "http.method"
    );
    // kafka collector config
    private boolean enableKafkaCollector = false;
    /**
     *  A list of host/port pairs to use for establishing the initial connection to the Kafka cluster.
     */
    private String kafkaBootstrapServers;

    private String kafkaGroupId = "zipkin";

    private String kafkaTopic = "zipkin";

    /**
     * Kafka consumer config,JSON format as Properties. If it contains the same key with above, would override.
     */
    private String kafkaConsumerConfig = "{\"auto.offset.reset\":\"earliest\",\"enable.auto.commit\":true}";

    private int kafkaConsumers = 1;

    private int kafkaHandlerThreadPoolSize;

    private int kafkaHandlerThreadPoolQueueSize;


}

