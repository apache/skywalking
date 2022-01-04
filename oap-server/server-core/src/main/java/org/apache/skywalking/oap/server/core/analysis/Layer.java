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

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.skywalking.oap.server.core.UnexpectedException;

/**
 * Layer represents an abstract framework in computer science, such as Operating System(OS_LINUX layer),
 * Kubernetes(k8s layer). This kind of layer would be owners of different services/instances detected from different technology.
 */
public enum Layer {
    /**
     * Default Layer if the layer is not defined
     */
    UNDEFINED(0),

    /**
     * Envoy Access Log Service
     */
    MESH(1),

    /**
     * Agent-installed Service
     */
    GENERAL(2),

    /**
     * Operation System Linux
     */
    OS_LINUX(3),

    /**
     * Kubernetes, include pods, services, contains etc.
     */
    K8S(4),

    /**
     * Function as a Service
     */
    FAAS(5),

    /**
     * Mesh control plane, eg. Istio control plane
     */
    MESH_CP(6),

    /**
     * Mesh data plane, eg. Envoy
     */
    MESH_DP(7),

    /**
     * Telemetry from real database
     */
    DATABASE(8),

    /**
     * Cache service eg. ehcache, guava-cache, memcache
     */
    CACHE(9),

    /**
     * Telemetry from the Browser eg. Apache SkyWalking Client JS
     */
    BROWSER(10),

    /**
     * Self Observability of OAP
     */
    SO11Y_OAP(11),

    /**
     * Self Observability of Satellite
     */
    SO11Y_SATELLITE(12),

    /**
     * Telemetry from the real MQ
     */
    MQ(13),

    /**
     * Database conjectured by client side plugin
     */
    VIRTUAL_DATABASE(14),

    /**
     * MQ conjectured by client side plugin
     */
    VIRTUAL_MQ(15);

    private final int value;
    private static final Map<Integer, Layer> DICTIONARY = new HashMap<>();

    static {
        Arrays.stream(Layer.values()).collect(Collectors.toMap(Layer::value, layer -> layer)).forEach(DICTIONARY::put);
    }

    Layer(int value) {
        this.value = value;
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
}
