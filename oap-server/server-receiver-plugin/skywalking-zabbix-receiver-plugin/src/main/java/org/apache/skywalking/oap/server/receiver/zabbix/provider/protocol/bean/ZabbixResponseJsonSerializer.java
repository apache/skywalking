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

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import java.lang.reflect.Type;

/**
 * Build the Zabbix response json
 */
public class ZabbixResponseJsonSerializer implements JsonSerializer<ZabbixResponse> {

    @Override
    public JsonElement serialize(ZabbixResponse src, Type typeOfSrc, JsonSerializationContext context) {
        ZabbixProtocolType type = src.getType();

        JsonObject response = new JsonObject();
        response.addProperty("response", "success");

        if (type == ZabbixProtocolType.ACTIVE_CHECKS) {
            response.add("data", new Gson().toJsonTree(src.getActiveChecks()));
        } else if (type == ZabbixProtocolType.AGENT_DATA) {
            response.addProperty("info", src.getAgentData().getInfo());
        }

        return response;
    }
}
