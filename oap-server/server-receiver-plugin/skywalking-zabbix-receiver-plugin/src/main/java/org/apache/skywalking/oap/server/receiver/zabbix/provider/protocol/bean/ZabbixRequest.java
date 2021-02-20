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

import lombok.Data;

import java.util.List;

@Data
public class ZabbixRequest {

    /**
     * Request type
     */
    private ZabbixProtocolType type;

    /**
     * Active checks data
     * @see ZabbixProtocolType#ACTIVE_CHECKS
     */
    private ActiveChecks activeChecks;

    /**
     * Agent push data
     * @see ZabbixProtocolType#AGENT_DATA
     */
    private List<AgentData> agentDataList;

    @Data
    public static class ActiveChecks {
        private String hostName;
    }

    @Data
    public static class AgentData {
        private String host;
        private String key;
        private String value;
        private int id;
        private long clock;
        private long ns;
        private int state;
    }

}
