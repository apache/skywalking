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

import org.apache.skywalking.apm.network.language.agent.v3.SpanLayer;
import org.apache.skywalking.oap.server.core.UnexpectedException;

/**
 * Node type describe which kind of node of Service or Network address represents to. The node with {@link #Normal} and
 * {@link #Browser} type would be treated as an observed node.
 */
public enum NodeType {
    /**
     * <code>Normal = 0;</code>
     * This node type would be treated as an observed node.
     */
    Normal(0),
    /**
     * <code>Database = 1;</code>
     */
    Database(1),
    /**
     * <code>RPCFramework = 2;</code>
     */
    RPCFramework(2),
    /**
     * <code>Http = 3;</code>
     */
    Http(3),
    /**
     * <code>MQ = 4;</code>
     */
    MQ(4),
    /**
     * <code>Cache = 5;</code>
     */
    Cache(5),
    /**
     * <code>Browser = 6;</code>
     * This node type would be treated as an observed node.
     */
    Browser(6),
    /**
     * <code>User = 10</code>
     */
    User(10),
    /**
     * <code>Unrecognized = 11</code>
     */
    Unrecognized(11);

    private final int value;

    NodeType(int value) {
        this.value = value;
    }

    public int value() {
        return value;
    }

    public static NodeType valueOf(int value) {
        switch (value) {
            case 0:
                return Normal;
            case 1:
                return Database;
            case 2:
                return RPCFramework;
            case 3:
                return Http;
            case 4:
                return MQ;
            case 5:
                return Cache;
            case 6:
                return Browser;
            case 10:
                return User;
            case 11:
                return Unrecognized;
            default:
                throw new UnexpectedException("Unknown NodeType value");
        }
    }

    /**
     * @return the node type conjectured from the give span layer.
     */
    public static NodeType fromSpanLayerValue(SpanLayer spanLayer) {
        switch (spanLayer) {
            case Unknown:
                return Normal;
            case Database:
                return Database;
            case RPCFramework:
                return RPCFramework;
            case Http:
                return Http;
            case MQ:
                return MQ;
            case Cache:
                return Cache;
            case UNRECOGNIZED:
                return Unrecognized;
            default:
                throw new UnexpectedException("Can't transfer to the NodeType. SpanLayer=" + spanLayer);
        }
    }
}
