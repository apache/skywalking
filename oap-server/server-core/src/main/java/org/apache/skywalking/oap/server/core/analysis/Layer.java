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

package org.apache.skywalking.oap.server.core.analysis;

import org.apache.skywalking.oap.server.core.UnexpectedException;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Layer represents an abstract framework in computer science, such as Operating System(OS_LINUX layer), Kubernetes(k8s
 * layer). This kind of layer would be owners of different services detected from different technology.
 */
public enum Layer {
    /**
     * Default Layer if the layer is not defined
     */
    UNDEFINED(0, false),

    /**
     * Envoy Access Log Service
     */
    MESH(1, true),

    /**
     * Agent-installed Service
     */
    GENERAL(2, true),

    /**
     * Linux Machine
     */
    OS_LINUX(3, true),

    /**
     * Kubernetes cluster
     */
    K8S(4, true),

    /**
     * Function as a Service
     *
     * Deprecated since 9.7.0. OpenFunction relative features are not maintained anymore.
     */
    @Deprecated
    FAAS(5, true),

    /**
     * Mesh control plane, eg. Istio control plane
     */
    MESH_CP(6, true),

    /**
     * Mesh data plane, eg. Envoy
     */
    MESH_DP(7, true),

    /**
     * Telemetry from real database
     */
    DATABASE(8, true),

    /**
     * Cache service eg. ehcache, guava-cache, memcache
     */
    CACHE(9, true),

    /**
     * Telemetry from the Browser eg. Apache SkyWalking Client JS
     */
    BROWSER(10, true),

    /**
     * Self Observability of OAP
     */
    SO11Y_OAP(11, true),

    /**
     * Self Observability of Satellite
     */
    SO11Y_SATELLITE(12, true),

    /**
     * Telemetry from the real MQ
     */
    MQ(13, true),

    /**
     * Database conjectured by client side plugin
     */
    VIRTUAL_DATABASE(14, false),

    /**
     * MQ conjectured by client side plugin
     */
    VIRTUAL_MQ(15, false),

    /**
     * The uninstrumented gateways configured in OAP
     */
    VIRTUAL_GATEWAY(16, false),

    /**
     * Kubernetes service
     */
    K8S_SERVICE(17, true),

    /**
     * MySQL Server, also known as mysqld, is a single multithreaded program that does most of the work in a MySQL
     * installation.
     */
    MYSQL(18, true),

    /**
     * Cache conjectured by client side plugin(eg. skywalking-java -&gt; JedisPlugin LettucePlugin)
     */
    VIRTUAL_CACHE(19, false),

    /**
     * PostgreSQL is an advanced, enterprise-class, and open-source relational database system.
     */
    POSTGRESQL(20, true),

    /**
     * Apache APISIX is an open source, dynamic, scalable, and high-performance cloud native API gateway.
     */
    APISIX(21, true),

    /**
     * EKS (Amazon Elastic Kubernetes Service) is k8s service provided by AWS Cloud
     */
    AWS_EKS(22, true),

    /**
     * Windows Machine
     */
    OS_WINDOWS(23, true),

    /**
     * Amazon Simple Storage Service (Amazon S3) is an object storage service provided by AWS Cloud
     */
    AWS_S3(24, true),

    /**
     * Amazon DynamoDB is a fully managed NoSQL database service that provides
     * fast and predictable performance with seamless scalability.
     */
    AWS_DYNAMODB(25, true),

    /**
     * Amazon API Gateway is an AWS service for creating, publishing, maintaining, monitoring, and securing REST, HTTP,
     * and WebSocket APIs at any scale.
     */
    AWS_GATEWAY(26, true),

    /**
     * Redis is an open source (BSD licensed), in-memory data structure store,
     * used as a database, cache, and message broker.
     */
    REDIS(27, true),

    /**
     * Elasticsearch is a distributed, open source search and analytics engine for all types of data,
     * including textual, numerical, geospatial, structured, and unstructured.
     */
    ELASTICSEARCH(28, true),

    /**
     * RabbitMQ is one of the most popular open source message brokers. RabbitMQ is lightweight and easy to deploy
     * on premises and in the cloud. It supports multiple messaging protocols.
     */
    RABBITMQ(29, true),

    /**
     * MongoDB is a document database. It stores data in a type of JSON format called BSON.
     */
    MONGODB(30, true),

    /**
     * Kafka is a distributed streaming platform that is used publish and subscribe to streams of records.
     */
    KAFKA(31, true),

    /**
     * Pulsar is a distributed pub-sub messaging platform that provides high-performance, durable messaging.
     * It is used to publish and subscribe to streams of records.
     * Pulsar supports scalable and fault-tolerant messaging, making it suitable for use in distributed systems.
     */
    PULSAR(32, true),

    /**
     * A scalable, fault-tolerant, and low-latency storage service optimized for real-time workloads.
     */
    BOOKKEEPER(33, true),

    /**
     * Nginx is an HTTP and reverse proxy server, a mail proxy server, and a generic TCP/UDP proxy server.
     */
    NGINX(34, true),

    /**
     * A cloud native messaging and streaming platform, making it simple to build event-driven applications.
     */
    ROCKETMQ(35, true),

    /**
     * A high-performance, column-oriented SQL database management system (DBMS) for online analytical processing (OLAP).
     */
    CLICKHOUSE(36, true),

    /**
     *  ActiveMQ is a popular open source, multi-protocol, Java-based message broker.
     */
    ACTIVEMQ(37, true),

    /**
     * Cilium is open source software for providing and transparently securing network connectivity and load balancing
     * between application workloads such as application containers or processes.
     */
    CILIUM_SERVICE(38, true),

    /**
     * The self observability of SkyWalking Java Agent,
     * which provides the abilities to measure the tracing performance and error statistics of plugins.
     */
    SO11Y_JAVA_AGENT(39, true),

    /**
     * Kong is Cloud-Native API Gateway and AI Gateway.
     */
    KONG(40, true);

    private final int value;
    /**
     * The `normal` status represents this service is detected by an agent. The `un-normal` service is conjectured by
     * telemetry data collected from agents on/in the `normal` service.
     */
    private final boolean isNormal;
    private static final Map<Integer, Layer> DICTIONARY = new HashMap<>();
    private static final Map<String, Layer> DICTIONARY_NAME = new HashMap<>();

    static {
        Arrays.stream(Layer.values()).forEach(l -> {
            DICTIONARY.put(l.value, l);
            DICTIONARY_NAME.put(l.name(), l);
        });
    }

    Layer(int value, boolean isNormal) {
        this.value = value;
        this.isNormal = isNormal;
    }

    public int value() {
        return value;
    }

    public static Layer valueOf(int value) {
        Layer layer = DICTIONARY.get(value);
        if (layer == null) {
            throw new UnexpectedException("Unknown Layer value");
        }
        return layer;
    }

    public static Layer nameOf(String name) {
        Layer layer = DICTIONARY_NAME.get(name);
        if (layer == null) {
            return UNDEFINED;
        }
        return layer;
    }

    public boolean isNormal() {
        return isNormal;
    }
}
