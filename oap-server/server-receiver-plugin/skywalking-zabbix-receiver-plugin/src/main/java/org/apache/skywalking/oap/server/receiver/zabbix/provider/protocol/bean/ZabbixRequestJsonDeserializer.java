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

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.List;

/**
 * Deserialize request from the Zabbix
 */
public class ZabbixRequestJsonDeserializer implements JsonDeserializer<ZabbixRequest> {
    @Override
    public ZabbixRequest deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        String requestTypeString = json.getAsJsonObject().get("request").getAsString();

        // Check has support request type
        ZabbixProtocolType requestType = ZabbixProtocolType.parse(requestTypeString);
        if (requestType == null) {
            throw new JsonParseException("Current request type is not support:" + requestTypeString);
        }

        // Build data
        ZabbixRequest data = new ZabbixRequest();
        data.setType(requestType);

        if (requestType == ZabbixProtocolType.AGENT_DATA) {
            data.setAgentDataList(context
                .deserialize(json.getAsJsonObject().getAsJsonArray("data"),
                    new TypeToken<List<ZabbixRequest.AgentData>>() {
                    }.getType()));
        } else if (requestType == ZabbixProtocolType.ACTIVE_CHECKS) {
            ZabbixRequest.ActiveChecks checksData = new ZabbixRequest.ActiveChecks();
            checksData.setHostName(json.getAsJsonObject().get("host").getAsString());
            data.setActiveChecks(checksData);
        }

        return data;
    }
}
