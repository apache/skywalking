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

package org.apache.skywalking.oap.server.core.config;

import org.apache.skywalking.oap.server.configuration.api.ConfigChangeWatcher.ConfigChangeEvent;
import org.apache.skywalking.oap.server.configuration.api.ConfigChangeWatcher.EventType;
import org.apache.skywalking.oap.server.core.CoreModuleProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class ServerClusterConfigTest {
    private static final String KAFKA_AND_PG = String.join("\n",
        "clusters:",
        "  - name: kafka-prod",
        "    members:",
        "      - kafka-0.kafka-headless:9092",
        "      - kafka-1.kafka-headless:9092",
        "      - nats://pinned:4222",
        "    aliases:",
        "      - kafka.prod.svc:9092",
        "      - redis-0:26379,redis-1:26379/mymaster",
        "  - name: pulsar-prod",
        "    scheme: pulsar://",
        "    members: [pulsar-0:6650, pulsar-1:6650]",
        "  - name: pulsar-tls",
        "    scheme: pulsar+ssl://",
        "    members: [pulsar-tls-0:6651]",
        "  - name: nats-prod",
        "    scheme: nats://",
        "    members: [nats-0:4222, nats-1:4222]",
        "  - name: orders-db",
        "    members:",
        "      - pg-0:5432",
        "      - pg-1:5432",
        "      - pg-2"
    );

    private ServerClusterConfig config;

    @BeforeEach
    public void setUp() {
        config = new ServerClusterConfig(new CoreModuleProvider(), 70);
        config.notify(new ConfigChangeEvent(KAFKA_AND_PG, EventType.MODIFY));
    }

    @Test
    public void theFileIsLoadedAtBoot() {
        final ServerClusterConfig fromFile = new ServerClusterConfig(new CoreModuleProvider(), 70);
        assertEquals("file-cluster", fromFile.findClusterName("file-broker:9092", null));
        assertNull(fromFile.value(), "the file is not a dynamic value");
    }

    @Test
    public void aWholeAddressMatchesAMemberOrAnAlias() {
        assertEquals("kafka-prod", config.findClusterName("kafka-0.kafka-headless:9092", null));
        assertEquals("kafka-prod", config.findClusterName("kafka.prod.svc:9092", null));
        assertEquals("orders-db", config.findClusterName("pg-2", null), "a bare host matches a bare host");
        assertEquals("kafka-prod", config.findClusterName("redis-0:26379,redis-1:26379/mymaster", null),
                     "an opaque reported value is matched whole");
    }

    @Test
    public void aListMatchesWhenEveryElementIsInOneClusterInAnyOrder() {
        assertEquals("kafka-prod", config.findClusterName("kafka-0.kafka-headless:9092;kafka-1.kafka-headless:9092", null));
        assertEquals("kafka-prod", config.findClusterName("kafka-1.kafka-headless:9092;kafka-0.kafka-headless:9092", null));
        assertEquals("kafka-prod", config.findClusterName("kafka-1.kafka-headless:9092", null), "a subset");
        assertEquals("orders-db", config.findClusterName("pg-1:5432,pg-0:5432", null), "comma separated");
        assertEquals("orders-db", config.findClusterName("pg-0:5432;pg-1:5432;", null), "a trailing separator");
        assertEquals("orders-db", config.findClusterName("pg-0:5432, pg-2", null), "spaces around elements");
    }

    @Test
    public void aListIsNotRenamedUnlessEveryElementIsInTheSameCluster() {
        assertNull(config.findClusterName("kafka-0.kafka-headless:9092;pg-0:5432", null), "two clusters");
        assertNull(config.findClusterName("kafka-0.kafka-headless:9092;unknown:9092", null), "an unlisted element");
        assertNull(config.findClusterName("pg-2:5432", null), "no port is inferred");
        assertNull(config.findClusterName(";;", null));
        assertNull(config.findClusterName("", null));
        assertNull(config.findClusterName(null, null));
    }

    @Test
    public void anElementWithTheClustersSchemeMatchesThatCluster() {
        assertEquals("pulsar-prod", config.findClusterName("pulsar://pulsar-0:6650,pulsar-1:6650", null),
                     "a service URL with the scheme on its first host");
        assertEquals("nats-prod", config.findClusterName("nats://nats-1:4222,nats://nats-0:4222", null),
                     "a scheme on every element");
        assertEquals("pulsar-prod", config.findClusterName("pulsar://pulsar-1:6650", null), "a single address");
        assertEquals("pulsar-tls", config.findClusterName("pulsar+ssl://pulsar-tls-0:6651", null),
                     "the prefix as the cluster declares it");
        assertNull(config.findClusterName("pulsar+ssl://pulsar-0:6650", null), "a prefix the cluster does not declare");
        assertEquals("kafka-prod", config.findClusterName("nats://pinned:4222", null), "listed with its scheme");
        assertNull(config.findClusterName("nats://pulsar-0:6650", null), "another cluster's scheme");
        assertNull(config.findClusterName("pulsar://kafka.prod.svc:9092", null), "a cluster that declares no scheme");
    }

    @Test
    public void aKindMatchesTheClustersThatListItOrListNone() {
        config.notify(new ConfigChangeEvent(String.join("\n",
            "clusters:",
            "  - name: orders-pg",
            "    kinds: [postgresql]",
            "    aliases: [orders:5432]",
            "  - name: orders-queue",
            "    kinds: [kafka, queue]",
            "    aliases: [orders:9092]",
            "  - name: any-kind",
            "    aliases: [shared:1]"
        ), EventType.MODIFY));
        assertEquals("orders-pg", config.findClusterName("orders:5432", "postgresql"));
        assertNull(config.findClusterName("orders:5432", "kafka"), "a kind the cluster does not list");
        assertEquals("orders-queue", config.findClusterName("orders:9092", "queue"));
        assertEquals("any-kind", config.findClusterName("shared:1", "queue"), "a cluster that lists no kind");
        assertEquals("orders-pg", config.findClusterName("orders:5432", null), "no kind asked for");
        assertNull(config.findClusterName("orders:5432,orders:9092", null), "two clusters");
    }

    @Test
    public void anInvalidUpdateKeepsThePreviousClusters() {
        for (final String invalid : new String[] {
            "clusters:\n  - name: a\n    aliases: [x:1]\n  - name: b\n    members: [x:1]",
            "clusters:\n  - name: a\n  - name: a",
            "clusters:\n  - aliases: [x:1]",
            "clusters:\n  - name: a\n    aliases: ['']",
            "clusters:\n  - name: a\n    aliases: [on]",
            "clusters:\n  - name: a\n    aliases: [[x:1, y:1]]",
            "clusters:\n  - name: a\n    kinds: [1]\n    aliases: [x:1]",
            "clusters:\n  - name: a\n    alias: [x:1]",
            "cluster:\n  - name: a",
            "clusters:\n  - name: a\n    scheme: ''\n    aliases: [x:1]",
            "clusters:\n  - name: a\n    scheme: [pulsar://]\n    aliases: [x:1]",
            "schemes: [pulsar]",
            "clusters:\n  - name: " + "n".repeat(71)
        }) {
            config.notify(new ConfigChangeEvent(invalid, EventType.MODIFY));
            assertEquals("kafka-prod", config.findClusterName("kafka.prod.svc:9092", null), invalid);
        }
    }

    @Test
    public void anUpdateReplacesTheClustersAndADeleteRestoresTheFile() {
        config.notify(new ConfigChangeEvent("clusters:\n  - name: other\n    aliases: [kafka.prod.svc:9092]",
                                            EventType.MODIFY));
        assertEquals("other", config.findClusterName("kafka.prod.svc:9092", null));
        assertNull(config.findClusterName("pg-0:5432", null), "the previous clusters are gone");

        config.notify(new ConfigChangeEvent(null, EventType.DELETE));
        assertNull(config.value());
        assertNull(config.findClusterName("kafka.prod.svc:9092", null));
        assertEquals("file-cluster", config.findClusterName("file-broker:9092", null));
    }
}
