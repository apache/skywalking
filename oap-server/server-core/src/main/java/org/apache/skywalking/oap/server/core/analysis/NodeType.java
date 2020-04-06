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
 * Node type describe which kind of node of Service or Network address represents to.
 * <p>
 * The value comes from 'org.apache.skywalking.apm.network.language.agent.SpanLayer' at first place, but most likely it
 * will extend and be used directly from different sources, such as Mesh.
 */
public enum NodeType {
    /**
     * <code>Unknown = 0;</code>
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
     */
    Browser(6),
    /**
     * <code>User = 10</code>
     */
    User(10),
    /**
     * <code>User = 11</code>
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
            case 10:
                return User;
            case 11:
                return Unrecognized;
            default:
                throw new UnexpectedException("Unknown NodeType value");
        }
    }

    /**
     * Right now, spanLayerValue is exact same as NodeType value.
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
                throw new UnexpectedException("Unknown NodeType value");
        }
    }
}
