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

import org.junit.Test;
import org.powermock.reflect.Whitebox;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;

public class KafkaProducerManagerTest {
    @Test
    public void testAddListener() throws Exception {
        KafkaProducerManager kafkaProducerManager = new KafkaProducerManager();
        AtomicInteger counter = new AtomicInteger();
        int times = 100;
        for (int i = 0; i < times; i++) {
            kafkaProducerManager.addListener(new MockListener(counter));
        }
        Whitebox.invokeMethod(kafkaProducerManager, "notifyListeners", KafkaConnectionStatus.CONNECTED);

        assertEquals(counter.get(), times);
    }

    @Test
    public void testFormatTopicNameThenRegister() {
        KafkaProducerManager kafkaProducerManager = new KafkaProducerManager();
        KafkaReporterPluginConfig.Plugin.Kafka.NAMESPACE = "product";
        String value = kafkaProducerManager.formatTopicNameThenRegister(KafkaReporterPluginConfig.Plugin.Kafka.TOPIC_METRICS);
        String expectValue = KafkaReporterPluginConfig.Plugin.Kafka.NAMESPACE + "-" + KafkaReporterPluginConfig.Plugin.Kafka.TOPIC_METRICS;
        assertEquals(value, expectValue);

        KafkaReporterPluginConfig.Plugin.Kafka.NAMESPACE = "";
        value = kafkaProducerManager.formatTopicNameThenRegister(KafkaReporterPluginConfig.Plugin.Kafka.TOPIC_METRICS);
        assertEquals(KafkaReporterPluginConfig.Plugin.Kafka.TOPIC_METRICS, value);
    }

    static class MockListener implements KafkaConnectionStatusListener {

        private AtomicInteger counter;

        public MockListener(AtomicInteger counter) {
            this.counter = counter;
        }

        @Override
        public void onStatusChanged(KafkaConnectionStatus status) {
            counter.incrementAndGet();
        }
    }

}
