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

import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.skywalking.oap.server.core.UnexpectedException;

/**
 * Layer represents an abstract framework in computer science, such as Operating System(OS_LINUX layer), Kubernetes(k8s
 * layer). This kind of layer would be owners of different services detected from different technology.
 *
 * <p>A registry-backed value type. Built-in layers are declared as {@code public static final}
 * constants below; external layers are contributed at boot through
 * {@link #register(String, int, boolean)}. Lookups are by name ({@link #nameOf(String)},
 * {@link #valueOf(String)}) or by ordinal ({@link #valueOf(int)}); {@link #values()} returns
 * every registered layer sorted by ordinal.
 *
 * <p>Layers are persisted by ordinal (int). Built-in ordinals 0–49 are frozen forever and
 * must never be reused. Ordinals 50–999 are reserved by convention for future built-ins;
 * external extensions are <strong>recommended</strong> to start at {@code 1000} to avoid
 * colliding with future built-ins on OAP upgrade. The recommendation is informational and
 * not enforced — any non-colliding ordinal is accepted — but a future built-in landing on
 * the same ordinal as an extension layer will cause OAP boot to fail loudly via the
 * ordinal uniqueness check, which is the upgrade-time detection mechanism.
 *
 * <p>Registration paths (all funnel through {@link #register}):
 * <ol>
 *   <li>Operator yaml — {@code layer-extensions.yml} on the OAP classpath/config dir.</li>
 *   <li>Java SPI — {@code LayerExtension} discovered via {@code ServiceLoader}.</li>
 *   <li>DSL inline — MAL/LAL rule files declaring a top-level {@code layerDefinitions:}
 *       block; the DSL loader funnels each entry through this method before compiling.</li>
 * </ol>
 * The registry is sealed at the start of {@code CoreModuleProvider.notifyAfterCompleted()};
 * any registration attempt after that throws.
 */
public final class Layer {

    private static final Pattern NAME_PATTERN = Pattern.compile("[A-Z][A-Z0-9_]*");

    private static final Map<Integer, Layer> BY_VALUE = new ConcurrentHashMap<>();
    private static final Map<String, Layer> BY_NAME = new ConcurrentHashMap<>();
    private static volatile boolean SEALED = false;
    /**
     * Snapshot of all registered layers sorted by ordinal, frozen at {@link #seal()}.
     * Before seal, {@link #values()} computes on the fly so newly registered layers are
     * visible; after seal, {@link #values()} returns a clone of this cached array.
     */
    private static volatile Layer[] CACHED_VALUES = null;

    /** Default Layer if the layer is not defined */
    public static final Layer UNDEFINED = register("UNDEFINED", 0, false);

    /** Envoy Access Log Service */
    public static final Layer MESH = register("MESH", 1, true);

    /** Agent-installed Service */
    public static final Layer GENERAL = register("GENERAL", 2, true);

    /** Linux Machine */
    public static final Layer OS_LINUX = register("OS_LINUX", 3, true);

    /** Kubernetes cluster */
    public static final Layer K8S = register("K8S", 4, true);

    /**
     * Function as a Service. Deprecated since 9.7.0. OpenFunction relative features are not maintained anymore.
     */
    @Deprecated
    public static final Layer FAAS = register("FAAS", 5, true);

    /** Mesh control plane, eg. Istio control plane */
    public static final Layer MESH_CP = register("MESH_CP", 6, true);

    /** Mesh data plane, eg. Envoy */
    public static final Layer MESH_DP = register("MESH_DP", 7, true);

    /** Telemetry from real database */
    public static final Layer DATABASE = register("DATABASE", 8, true);

    /** Cache service eg. ehcache, guava-cache, memcache */
    public static final Layer CACHE = register("CACHE", 9, true);

    /** Telemetry from the Browser eg. Apache SkyWalking Client JS */
    public static final Layer BROWSER = register("BROWSER", 10, true);

    /** Self Observability of OAP */
    public static final Layer SO11Y_OAP = register("SO11Y_OAP", 11, true);

    /** Self Observability of Satellite */
    public static final Layer SO11Y_SATELLITE = register("SO11Y_SATELLITE", 12, true);

    /** Telemetry from the real MQ */
    public static final Layer MQ = register("MQ", 13, true);

    /** Database conjectured by client side plugin */
    public static final Layer VIRTUAL_DATABASE = register("VIRTUAL_DATABASE", 14, false);

    /** MQ conjectured by client side plugin */
    public static final Layer VIRTUAL_MQ = register("VIRTUAL_MQ", 15, false);

    /** The uninstrumented gateways configured in OAP */
    public static final Layer VIRTUAL_GATEWAY = register("VIRTUAL_GATEWAY", 16, false);

    /** Kubernetes service */
    public static final Layer K8S_SERVICE = register("K8S_SERVICE", 17, true);

    /**
     * MySQL Server, also known as mysqld, is a single multithreaded program that does most of the work in a MySQL
     * installation.
     */
    public static final Layer MYSQL = register("MYSQL", 18, true);

    /** Cache conjectured by client side plugin(eg. skywalking-java -&gt; JedisPlugin LettucePlugin) */
    public static final Layer VIRTUAL_CACHE = register("VIRTUAL_CACHE", 19, false);

    /** PostgreSQL is an advanced, enterprise-class, and open-source relational database system. */
    public static final Layer POSTGRESQL = register("POSTGRESQL", 20, true);

    /** Apache APISIX is an open source, dynamic, scalable, and high-performance cloud native API gateway. */
    public static final Layer APISIX = register("APISIX", 21, true);

    /** EKS (Amazon Elastic Kubernetes Service) is k8s service provided by AWS Cloud */
    public static final Layer AWS_EKS = register("AWS_EKS", 22, true);

    /** Windows Machine */
    public static final Layer OS_WINDOWS = register("OS_WINDOWS", 23, true);

    /** Amazon Simple Storage Service (Amazon S3) is an object storage service provided by AWS Cloud */
    public static final Layer AWS_S3 = register("AWS_S3", 24, true);

    /**
     * Amazon DynamoDB is a fully managed NoSQL database service that provides
     * fast and predictable performance with seamless scalability.
     */
    public static final Layer AWS_DYNAMODB = register("AWS_DYNAMODB", 25, true);

    /**
     * Amazon API Gateway is an AWS service for creating, publishing, maintaining, monitoring, and securing REST, HTTP,
     * and WebSocket APIs at any scale.
     */
    public static final Layer AWS_GATEWAY = register("AWS_GATEWAY", 26, true);

    /**
     * Redis is an open source (BSD licensed), in-memory data structure store,
     * used as a database, cache, and message broker.
     */
    public static final Layer REDIS = register("REDIS", 27, true);

    /**
     * Elasticsearch is a distributed, open source search and analytics engine for all types of data,
     * including textual, numerical, geospatial, structured, and unstructured.
     */
    public static final Layer ELASTICSEARCH = register("ELASTICSEARCH", 28, true);

    /**
     * RabbitMQ is one of the most popular open source message brokers. RabbitMQ is lightweight and easy to deploy
     * on premises and in the cloud. It supports multiple messaging protocols.
     */
    public static final Layer RABBITMQ = register("RABBITMQ", 29, true);

    /** MongoDB is a document database. It stores data in a type of JSON format called BSON. */
    public static final Layer MONGODB = register("MONGODB", 30, true);

    /** Kafka is a distributed streaming platform that is used publish and subscribe to streams of records. */
    public static final Layer KAFKA = register("KAFKA", 31, true);

    /**
     * Pulsar is a distributed pub-sub messaging platform that provides high-performance, durable messaging.
     * It is used to publish and subscribe to streams of records.
     * Pulsar supports scalable and fault-tolerant messaging, making it suitable for use in distributed systems.
     */
    public static final Layer PULSAR = register("PULSAR", 32, true);

    /** A scalable, fault-tolerant, and low-latency storage service optimized for real-time workloads. */
    public static final Layer BOOKKEEPER = register("BOOKKEEPER", 33, true);

    /** Nginx is an HTTP and reverse proxy server, a mail proxy server, and a generic TCP/UDP proxy server. */
    public static final Layer NGINX = register("NGINX", 34, true);

    /** A cloud native messaging and streaming platform, making it simple to build event-driven applications. */
    public static final Layer ROCKETMQ = register("ROCKETMQ", 35, true);

    /**
     * A high-performance, column-oriented SQL database management system (DBMS) for online analytical processing (OLAP).
     */
    public static final Layer CLICKHOUSE = register("CLICKHOUSE", 36, true);

    /** ActiveMQ is a popular open source, multi-protocol, Java-based message broker. */
    public static final Layer ACTIVEMQ = register("ACTIVEMQ", 37, true);

    /**
     * Cilium is open source software for providing and transparently securing network connectivity and load balancing
     * between application workloads such as application containers or processes.
     */
    public static final Layer CILIUM_SERVICE = register("CILIUM_SERVICE", 38, true);

    /**
     * The self observability of SkyWalking Java Agent,
     * which provides the abilities to measure the tracing performance and error statistics of plugins.
     */
    public static final Layer SO11Y_JAVA_AGENT = register("SO11Y_JAVA_AGENT", 39, true);

    /** Kong is Cloud-Native API Gateway and AI Gateway. */
    public static final Layer KONG = register("KONG", 40, true);

    /**
     * The self observability of SkyWalking Go Agent,
     * which provides the abilities to measure the tracing performance and error statistics of plugins.
     */
    public static final Layer SO11Y_GO_AGENT = register("SO11Y_GO_AGENT", 41, true);

    /**
     * Apache Flink is a framework and distributed processing engine for stateful computations over unbounded and bounded data streams
     */
    public static final Layer FLINK = register("FLINK", 42, true);

    /**
     * BanyanDB is a distributed time-series database with built-in self-monitoring for real-time tracking of system health, performance, and resource utilization.
     */
    public static final Layer BANYANDB = register("BANYANDB", 43, true);

    /** GenAI represents an instrumented Generative AI service or application. */
    public static final Layer GENAI = register("GENAI", 44, true);

    /**
     * Virtual GenAI is a virtual layer used to represent and monitor remote, uninstrumented
     * Generative AI providers.
     */
    public static final Layer VIRTUAL_GENAI = register("VIRTUAL_GENAI", 45, false);

    /**
     * Envoy AI Gateway is an AI/LLM traffic gateway built on Envoy Proxy,
     * providing observability for GenAI API traffic.
     */
    public static final Layer ENVOY_AI_GATEWAY = register("ENVOY_AI_GATEWAY", 46, true);

    /** iOS/iPadOS app monitoring via OpenTelemetry Swift SDK */
    public static final Layer IOS = register("IOS", 47, true);

    /** WeChat Mini Program monitoring via mini-program-monitor SDK */
    public static final Layer WECHAT_MINI_PROGRAM = register("WECHAT_MINI_PROGRAM", 48, true);

    /** Alipay Mini Program monitoring via mini-program-monitor SDK */
    public static final Layer ALIPAY_MINI_PROGRAM = register("ALIPAY_MINI_PROGRAM", 49, true);

    private final String name;
    private final int value;
    /**
     * The `normal` status represents this service is detected by an agent. The `un-normal` service is conjectured by
     * telemetry data collected from agents on/in the `normal` service.
     */
    private final boolean isNormal;

    private Layer(final String name, final int value, final boolean isNormal) {
        this.name = name;
        this.value = value;
        this.isNormal = isNormal;
    }

    public String name() {
        return name;
    }

    public int value() {
        return value;
    }

    public boolean isNormal() {
        return isNormal;
    }

    @Override
    public String toString() {
        return name;
    }

    /**
     * Single registration path used by both the built-in static initializer above and by every
     * external source ({@code LayerExtensionLoader} for operator yaml + SPI, and the MAL/LAL DSL
     * loaders parsing inline {@code layerDefinitions:} blocks). Idempotent on identical
     * re-registration so the same extension loaded by multiple paths is harmless.
     *
     * @param name    upper-snake-case identifier; must match {@code [A-Z][A-Z0-9_]*}
     * @param value   ordinal unique across all layers (see class javadoc for the ordinal
     *                conventions and the {@code >= 1000} recommendation for extensions)
     * @param isNormal whether services in this layer are agent-installed (true) or conjectured (false)
     * @return the registered layer
     * @throws IllegalStateException     if the registry is sealed, or on a name/ordinal conflict
     * @throws IllegalArgumentException  if name shape is invalid
     */
    public static synchronized Layer register(final String name, final int value, final boolean isNormal) {
        if (SEALED) {
            throw new IllegalStateException(
                "Layer registry is sealed; cannot register " + name + "=" + value
                    + ". External layers must register before CoreModule.notifyAfterCompleted().");
        }
        if (name == null || !NAME_PATTERN.matcher(name).matches()) {
            throw new IllegalArgumentException(
                "Layer name must match [A-Z][A-Z0-9_]*: " + name);
        }
        final Layer existingByName = BY_NAME.get(name);
        if (existingByName != null) {
            if (existingByName.value == value && existingByName.isNormal == isNormal) {
                return existingByName;
            }
            throw new IllegalStateException(
                "Layer name conflict: " + name + " already registered as ordinal=" + existingByName.value
                    + ", normal=" + existingByName.isNormal
                    + "; refused re-registration as ordinal=" + value + ", normal=" + isNormal);
        }
        final Layer existingByValue = BY_VALUE.get(value);
        if (existingByValue != null) {
            throw new IllegalStateException(
                "Layer ordinal conflict at " + value
                    + ": existing=" + existingByValue.name + ", new=" + name);
        }
        final Layer layer = new Layer(name, value, isNormal);
        BY_VALUE.put(value, layer);
        BY_NAME.put(name, layer);
        return layer;
    }

    /**
     * Closes the registry. Subsequent {@link #register} calls throw. Called by
     * {@code CoreModuleProvider.notifyAfterCompleted()} after every module's prepare/start
     * has run, so MAL/LAL/SPI/yaml all had their full window. Idempotent.
     */
    public static synchronized void seal() {
        CACHED_VALUES = BY_VALUE.values()
                                .stream()
                                .sorted(Comparator.comparingInt(Layer::value))
                                .toArray(Layer[]::new);
        SEALED = true;
    }

    public static Layer valueOf(final int value) {
        final Layer layer = BY_VALUE.get(value);
        if (layer == null) {
            throw new UnexpectedException("Unknown Layer value: " + value);
        }
        return layer;
    }

    /**
     * Look up a layer by name, throwing on miss. Matches the prior enum-generated
     * {@code valueOf(String)} contract. Use {@link #nameOf(String)} for the lenient
     * variant that returns {@link #UNDEFINED} on miss.
     */
    public static Layer valueOf(final String name) {
        final Layer layer = BY_NAME.get(name);
        if (layer == null) {
            throw new IllegalArgumentException("No layer constant with name: " + name);
        }
        return layer;
    }

    public static Layer nameOf(final String name) {
        final Layer layer = BY_NAME.get(name);
        if (layer == null) {
            return UNDEFINED;
        }
        return layer;
    }

    /**
     * Snapshot of all registered layers, sorted by ordinal value. Returns a fresh array each
     * call to preserve enum-style mutation safety; the underlying snapshot is computed once
     * at {@link #seal()} so calls only pay the array-clone cost.
     *
     * <p>Throws if called before {@link #seal()} — pre-seal the registry may still grow as
     * MAL/LAL/SPI/yaml extensions register, so any caller iterating {@code values()} during
     * boot would silently snapshot a partial view. Callers that need a layer set during boot
     * are almost always wrong; either look up specific layers by name via {@link #nameOf},
     * or defer the iteration to a post-boot phase.
     */
    public static Layer[] values() {
        if (CACHED_VALUES == null) {
            throw new IllegalStateException(
                "Layer.values() called before the registry was sealed. "
                    + "External layers may still register during module prepare()/start(); "
                    + "iterating now would snapshot a partial view. "
                    + "Defer the iteration until after CoreModule.notifyAfterCompleted().");
        }
        return CACHED_VALUES.clone();
    }

    /** Renders the registry as {@code "NAME=ordinal,NAME=ordinal,..."} sorted by ordinal. */
    public static String describeRegistry() {
        return BY_VALUE.values()
                       .stream()
                       .sorted(Comparator.comparingInt(Layer::value))
                       .map(l -> l.name + "=" + l.value)
                       .collect(Collectors.joining(","));
    }
}
