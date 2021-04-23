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

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.utils.Bytes;
import org.apache.skywalking.oap.server.analyzer.agent.kafka.mock.MockModuleManager;
import org.apache.skywalking.oap.server.analyzer.agent.kafka.module.KafkaFetcherConfig;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class AbstractKafkaHandlerTest {
    @Test
    public void testGetTopic() {
        KafkaFetcherConfig config = new KafkaFetcherConfig();

        MockModuleManager manager = new MockModuleManager() {
            @Override
            protected void init() {
            }
        };
        String plainTopic = config.getTopicNameOfTracingSegments();

        MockKafkaHandler kafkaHandler = new MockKafkaHandler(plainTopic, manager, config);

        //  unset namespace and mm2
        assertEquals(kafkaHandler.getTopic(), plainTopic);

        //set namespace only
        String namespace = "product";
        config.setNamespace(namespace);
        assertEquals(namespace + "-" + plainTopic, kafkaHandler.getTopic());

        //set mm2 only
        config.setNamespace("");
        String mm2SourceAlias = "DC1";
        config.setMm2SourceAlias(mm2SourceAlias);
        String mm2SourceSeparator = ".";
        config.setMm2SourceSeparator(mm2SourceSeparator);
        assertEquals(mm2SourceAlias + mm2SourceSeparator + plainTopic, kafkaHandler.getTopic());

        //set namespace and mm2
        config.setNamespace(namespace);
        config.setMm2SourceAlias(mm2SourceAlias);
        config.setMm2SourceSeparator(mm2SourceSeparator);
        assertEquals(mm2SourceAlias + mm2SourceSeparator + namespace + "-" + plainTopic, kafkaHandler.getTopic());

    }

    static class MockKafkaHandler extends AbstractKafkaHandler {
        private String plainTopic;

        public MockKafkaHandler(String plainTopic, ModuleManager manager, KafkaFetcherConfig config) {
            super(manager, config);
            this.plainTopic = plainTopic;
        }

        @Override
        protected String getPlainTopic() {
            return plainTopic;
        }

        @Override
        public void handle(ConsumerRecord<String, Bytes> record) {

        }
    }
}
