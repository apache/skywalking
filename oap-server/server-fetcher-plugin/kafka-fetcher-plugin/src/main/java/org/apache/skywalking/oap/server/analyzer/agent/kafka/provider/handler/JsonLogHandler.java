/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.skywalking.oap.server.analyzer.agent.kafka.provider.handler;

import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.utils.Bytes;
import org.apache.skywalking.apm.network.logging.v3.LogData;
import org.apache.skywalking.oap.server.analyzer.agent.kafka.module.KafkaFetcherConfig;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.util.ProtoBufJsonUtils;

@Slf4j
public class JsonLogHandler extends LogHandler {

    private final KafkaFetcherConfig config;

    public JsonLogHandler(ModuleManager moduleManager, KafkaFetcherConfig config) {
        super(moduleManager, config);
        this.config = config;
    }

    @Override
    public String getTopic() {
        return config.getTopicNameOfJsonLogs();
    }
    
    @Override
    protected String getDataFormat() {
        return "json";
    }
    
    @Override
    protected LogData parseConsumerRecord(ConsumerRecord<String, Bytes> record) throws IOException {
        LogData.Builder logDataBuilder = LogData.newBuilder();
        ProtoBufJsonUtils.fromJSON(record.value().toString(), logDataBuilder);
        return logDataBuilder.build();
    }
}
