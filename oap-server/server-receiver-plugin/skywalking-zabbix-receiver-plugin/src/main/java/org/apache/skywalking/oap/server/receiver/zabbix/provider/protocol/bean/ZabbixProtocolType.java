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

package org.apache.skywalking.oap.server.receiver.zabbix.provider.protocol.bean;

import lombok.Getter;

import java.util.Objects;

/**
 * Zabbix protocol type
 */
@Getter
public enum ZabbixProtocolType {
    ACTIVE_CHECKS("active checks"),
    AGENT_DATA("agent data")
    ;
    private String name;

    ZabbixProtocolType(String name) {
        this.name = name;
    }

    /**
     * Parse type by name
     */
    public static ZabbixProtocolType parse(String name) {
        for (ZabbixProtocolType type : ZabbixProtocolType.values()) {
            if (Objects.equals(type.name, name)) {
                return type;
            }
        }
        return null;
    }
}