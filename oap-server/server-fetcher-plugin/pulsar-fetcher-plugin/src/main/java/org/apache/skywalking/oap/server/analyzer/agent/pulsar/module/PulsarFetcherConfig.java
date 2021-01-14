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

package org.apache.skywalking.oap.server.analyzer.agent.pulsar.module;

import java.util.Properties;
import lombok.Data;
import org.apache.skywalking.oap.server.library.module.ModuleConfig;

@Data
public class PulsarFetcherConfig extends ModuleConfig {

    /**
     * Pulsar consumer config.
     */
    private Properties pulsarConsumerConfig = new Properties();

    /**
     * <B>serviceUrl</B>: A string to use for establishing the initial connection to the Pulsar cluster.
     */
    private String serviceUrl;

    /**
     * <B>subscriptionName</B>: A unique string that identifies the consumer group this consumer belongs to.
     */
    private String subscriptionName = "skywalking-consumer";

    private boolean enableMeterSystem = false;

    private String configPath = "meter-analyzer-config";

    private String topicNameOfMetrics = "skywalking-metrics";

    private String topicNameOfProfiling = "skywalking-profilings";

    private String topicNameOfTracingSegments = "skywalking-segments";

    private String topicNameOfManagements = "skywalking-managements";

    private String topicNameOfMeters = "skywalking-meters";

    private int pulsarHandlerThreadPoolSize;

    private int pulsarHandlerThreadPoolQueueSize;

    private String tenant = "public";

    public String namespace = "default";

}
