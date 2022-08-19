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

package org.apache.skywalking.e2e.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Random;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.skywalking.e2e.E2EConfiguration;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import zipkin2.Endpoint;
import zipkin2.Span;
import zipkin2.codec.SpanBytesEncoder;

@RestController
public class ZipkinKafkaProduceController {
    private final KafkaProducer<byte[], byte[]> producer;
    private final E2EConfiguration config;

    public ZipkinKafkaProduceController(final E2EConfiguration config) {
        this.config = config;
        Properties properties = new Properties();
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, config.getZipkinKafkaBootstrapServers());
        properties.setProperty(ConsumerConfig.GROUP_ID_CONFIG, config.getZipkinKafkaGroupId());
        producer = new KafkaProducer<>(properties, new ByteArraySerializer(), new ByteArraySerializer());
    }

    @PostMapping("/sendZipkinTrace2Kafka")
    public String sendTrace() {
        producer.send(new ProducerRecord<>(config.getZipkinKafkaTopic(), 0, null, SpanBytesEncoder.JSON_V2.encodeList(makeTrace())));
        producer.flush();
        return "Trace send success!";
    }

    private static List<Span> makeTrace() {
        String traceId = generateHexId(16);
        String span1Id = traceId;
        String span2Id = generateHexId(16);
        String span3Id = generateHexId(16);
        List<Span> trace = new ArrayList<>();
        trace.add(Span.newBuilder()
                      .traceId(traceId)
                      .id(span1Id)
                      .name("post /")
                      .kind(Span.Kind.SERVER)
                      .localEndpoint(Endpoint.newBuilder().serviceName("frontend").ip("192.168.0.1").build())
                      .remoteEndpoint(Endpoint.newBuilder().ip("127.0.0.1").port(63720).build())
                      .timestamp(System.currentTimeMillis() * 1000L + 1000L)
                      .duration(16683)
                      .addAnnotation(System.currentTimeMillis() * 1000L + 1100L, "wr")
                      .addAnnotation(System.currentTimeMillis() * 1000L + 1200L, "ws")
                      .putTag("http.method", "POST")
                      .putTag("http.path", "/")
                      .build());
        trace.add(Span.newBuilder()
                      .traceId(traceId)
                      .parentId(span1Id)
                      .id(span2Id)
                      .name("get")
                      .kind(Span.Kind.CLIENT)
                      .localEndpoint(Endpoint.newBuilder().serviceName("frontend").ip("192.168.0.1").build())
                      .remoteEndpoint(Endpoint.newBuilder().serviceName("backend").ip("127.0.0.1").port(9000).build())
                      .timestamp(System.currentTimeMillis() * 1000L + 2000L)
                      .duration(15380)
                      .addAnnotation(System.currentTimeMillis() * 1000L + 2100L, "wr")
                      .addAnnotation(System.currentTimeMillis() * 1000L + 2600L, "ws")
                      .putTag("http.method", "GET")
                      .putTag("http.path", "/api")
                      .build());
        trace.add(Span.newBuilder()
                      .traceId(traceId)
                      .parentId(span2Id)
                      .id(span3Id)
                      .name("get /api")
                      .kind(Span.Kind.SERVER)
                      .localEndpoint(Endpoint.newBuilder().serviceName("backend").ip("192.168.0.1").build())
                      .remoteEndpoint(Endpoint.newBuilder().ip("127.0.0.1").port(63722).build())
                      .timestamp(System.currentTimeMillis() * 1000L + 3000L)
                      .duration(1557)
                      .addAnnotation(System.currentTimeMillis() * 1000L + 3100L, "wr")
                      .addAnnotation(System.currentTimeMillis() * 1000L + 3300L, "ws")
                      .putTag("http.method", "GET")
                      .putTag("http.path", "/api")
                      .build());
        return trace;
    }

    private static String generateHexId(int bound) {
        Random r = new Random();
        StringBuffer buffer = new StringBuffer();
        for (int i = 0; i < bound; i++) {
            buffer.append(Integer.toHexString(r.nextInt(bound)));
        }
        return buffer.toString();
    }
}
