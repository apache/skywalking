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
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.skywalking.oap.server.core.UnexpectedException;

/**
 * Layer represents an abstract framework in the computer science, such as operation system(OS_LINUX layer), Kubernetes(k8s layer)
 */
public enum Layer {

    UNDEFINED(0),
    /**
     * Envoy Access Log Service
     */
    MESH(1),
    /**
     * Agent-installed Service
     */
    GENERAL(2),

    OS_LINUX(3),

    K8S(4),

    FAAS(5),
    /**
     * Mesh control plane, eg. Istio control plane
     */
    MESH_CP(6),
    /**
     * Mesh data plane, eg. Envoy
     */
    MESH_DP(7),

    DATABASE(8),

    CACHE(9),

    BROWSER(10),
    /**
     * Self Observability of OAP
     */
    SO11Y_OAP(11),
    /**
     * Self Observability of Satellite
     */
    SO11Y_SATELLITE(12),

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
    private static Map<Integer, Layer> DICTIONARY;

    Layer(int value) {
        this.value = value;
    }

    public int value() {
        return value;
    }

    public static Layer valueOf(int value) {
        if (DICTIONARY == null) {
            DICTIONARY = Arrays.stream(Layer.values()).collect(Collectors.toMap(Layer::value, layer -> layer));
        }
        Layer layer = DICTIONARY.get(value);
        if (layer == null) {
            throw new UnexpectedException("Unknown Layer value");
        }
        return layer;
    }
}
