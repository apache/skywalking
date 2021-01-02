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

package org.apache.skywalking.e2e.kafka;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.DescribeTopicsResult;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.BytesSerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.common.utils.Bytes;
import org.apache.skywalking.apm.network.common.v3.KeyStringValuePair;
import org.apache.skywalking.apm.network.logging.v3.LogData;
import org.apache.skywalking.apm.network.logging.v3.LogDataBody;
import org.apache.skywalking.apm.network.logging.v3.TextLog;
import org.apache.skywalking.apm.network.logging.v3.TraceContext;
import org.apache.skywalking.e2e.annotation.ContainerHostAndPort;
import org.apache.skywalking.e2e.annotation.DockerCompose;
import org.apache.skywalking.e2e.base.SkyWalkingE2E;
import org.apache.skywalking.e2e.base.SkyWalkingTestAdapter;
import org.apache.skywalking.e2e.base.TrafficController;
import org.apache.skywalking.e2e.common.HostAndPort;
import org.apache.skywalking.e2e.log.Log;
import org.apache.skywalking.e2e.log.LogsMatcher;
import org.apache.skywalking.e2e.log.LogsQuery;
import org.apache.skywalking.e2e.retryable.RetryableTest;
import org.apache.skywalking.e2e.service.Service;
import org.apache.skywalking.e2e.service.ServicesMatcher;
import org.apache.skywalking.e2e.service.ServicesQuery;
import org.apache.skywalking.e2e.service.endpoint.EndpointQuery;
import org.apache.skywalking.e2e.service.endpoint.Endpoints;
import org.apache.skywalking.e2e.service.endpoint.EndpointsMatcher;
import org.apache.skywalking.e2e.service.instance.Instances;
import org.apache.skywalking.e2e.service.instance.InstancesMatcher;
import org.apache.skywalking.e2e.service.instance.InstancesQuery;
import org.apache.skywalking.e2e.utils.Times;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.containers.DockerComposeContainer;

import static java.util.Objects.nonNull;
import static org.apache.skywalking.e2e.utils.Yamls.load;

@Slf4j
@SkyWalkingE2E
public class KafkaLogE2E extends SkyWalkingTestAdapter {

    private static final String TOPIC = "skywalking-logs";

    @SuppressWarnings("unused")
    @DockerCompose({
        "docker/kafka/docker-compose.log.yml"
    })
    private DockerComposeContainer<?> justForSideEffects;

    @SuppressWarnings("unused")
    @ContainerHostAndPort(name = "oap", port = 12800)
    private HostAndPort swWebappHostPort;

    @SuppressWarnings("unused")
    @ContainerHostAndPort(name = "oap", port = 11800)
    private HostAndPort oapHostPost;

    @ContainerHostAndPort(name = "broker-a", port = 9092)
    private HostAndPort kafkaBrokerA;

    @ContainerHostAndPort(name = "broker-b", port = 9092)
    private HostAndPort kafkaBrokerB;

    private KafkaProducer<String, Bytes> producer;

    @BeforeAll
    public void setUp() {
        queryClient(swWebappHostPort);
        initKafkaProducer();
        generateTraffic();
    }

    @AfterAll
    public void tearDown() {
        if (trafficController != null) {
            trafficController.stop();
        }
        if (producer != null) {
            producer.close();
        }
    }

    @RetryableTest
    public void verifyService() throws Exception {
        List<Service> services = graphql.services(
            new ServicesQuery().start(startTime).end(Times.now()));
        services = services.stream().filter(s -> !s.getLabel().equals("oap::oap-server")).collect(Collectors.toList());
        LOGGER.info("services: {}", services);

        load("expected/log/services.yml").as(ServicesMatcher.class).verify(services);

        for (Service service : services) {
            LOGGER.info("verifying service instance: {}", service);
            // instance
            verifyServiceInstances(service);
            // endpoint
            verifyServiceEndpoints(service);
        }
    }

    @RetryableTest
    public void verifyLog() throws Exception {
        final List<Log> logs = graphql.logs(new LogsQuery().start(startTime).end(Times.now()));
        LOGGER.info("logs: {}", logs);

        load("expected/log/logs.yml").as(LogsMatcher.class).verifyLoosely(logs);
    }

    private void verifyServiceInstances(final Service service) throws Exception {
        final Instances instances = graphql.instances(
            new InstancesQuery().serviceId(service.getKey()).start(startTime).end(Times.now()));

        LOGGER.info("instances: {}", instances);
        load("expected/log/instances.yml").as(InstancesMatcher.class).verify(instances);
    }

    private void verifyServiceEndpoints(final Service service) throws Exception {
        final Endpoints endpoints = graphql.endpoints(new EndpointQuery().serviceId(service.getKey()));
        LOGGER.info("endpoints: {}", endpoints);

        load("expected/log/endpoints.yml").as(EndpointsMatcher.class).verify(endpoints);
    }

    private void initKafkaProducer() {
        Properties properties = new Properties();
        properties.setProperty(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBrokerA + "," + kafkaBrokerB);
        producer = new KafkaProducer<>(properties, new StringSerializer(), new BytesSerializer());
        AdminClient adminClient = AdminClient.create(properties);
        DescribeTopicsResult topicsResult = adminClient.describeTopics(Collections.singletonList(TOPIC));
        Set<String> topics = topicsResult.values().entrySet().stream()
                                         .map(entry -> {
                                             try {
                                                 entry.getValue().get(10, TimeUnit.SECONDS);
                                                 return null;
                                             } catch (InterruptedException | ExecutionException | TimeoutException e) {
                                                 LOGGER.error("Get KAFKA topic:" + entry.getKey() + " error", e);
                                             }
                                             return entry.getKey();
                                         }).filter(Objects::nonNull).collect(Collectors.toSet());
        if (!topics.isEmpty()) {
            throw new RuntimeException("These topics" + topics + " don't exist.");
        }
    }

    private void generateTraffic() {
        trafficController = TrafficController.builder()
                                             .sender(this::sendLog)
                                             .build();
        trafficController.start();
    }

    private boolean sendLog() {
        try {
            LogData logData = LogData.newBuilder()
                                     .setTimestamp(System.currentTimeMillis())
                                     .setService("e2e")
                                     .setServiceInstance("e2e-instance")
                                     .setEndpoint("/traffic")
                                     .setBody(
                                         LogDataBody.newBuilder()
                                                    .setText(TextLog.newBuilder().setText("log").build())
                                                    .build())
                                     .addTags(
                                         KeyStringValuePair.newBuilder().setKey("status_code").setValue("200").build())
                                     .setTraceContext(TraceContext.newBuilder()
                                                                  .setTraceId("ac81b308-0d66-4c69-a7af-a023a536bd3e")
                                                                  .setTraceSegmentId(
                                                                      "6024a2b1fcff48e4a641d69d388bac53.41.16088574455279608")
                                                                  .setSpanId(0)
                                                                  .build())
                                     .build();

            producer.send(
                new ProducerRecord<>(TOPIC, logData.getService(), Bytes.wrap(logData.toByteArray())),
                (m, e) -> {
                    if (nonNull(e)) {
                        LOGGER.error("Failed to report logs.", e);
                    }
                }
            );
            return true;
        } catch (Exception e) {
            LOGGER.warn(e.getMessage(), e);
            return false;
        }
    }
}
