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

package org.apache.skywalking.oap.server.exporter.provider.kafka;

import com.google.gson.Gson;
import java.util.Properties;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.BytesSerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.common.utils.Bytes;
import org.apache.skywalking.oap.server.exporter.provider.ExporterSetting;
import org.apache.skywalking.oap.server.library.util.StringUtil;

@Slf4j
public abstract class KafkaExportProducer {
    protected final ExporterSetting setting;
    private volatile KafkaProducer<String, Bytes> producer;

    public KafkaExportProducer(ExporterSetting setting) {
        this.setting = setting;
    }

    protected KafkaProducer<String, Bytes> getProducer() {
        if (producer == null) {
            Properties properties = new Properties();
            properties.setProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, setting.getKafkaBootstrapServers());
            if (StringUtil.isNotEmpty(setting.getKafkaProducerConfig())) {
                Gson gson = new Gson();
                Properties override = gson.fromJson(setting.getKafkaProducerConfig(), Properties.class);
                properties.putAll(override);
            }
            producer = new KafkaProducer<>(properties, new StringSerializer(), new BytesSerializer());
        }
        return producer;
    }
}
