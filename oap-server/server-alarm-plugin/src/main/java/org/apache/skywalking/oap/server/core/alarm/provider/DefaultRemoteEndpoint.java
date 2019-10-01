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

package org.apache.skywalking.oap.server.core.alarm.provider;

import com.google.gson.Gson;
import org.apache.http.HttpEntity;
import org.apache.http.entity.StringEntity;
import org.apache.skywalking.oap.server.core.alarm.AlarmMessage;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * @author muyun12
 */
public class DefaultRemoteEndpoint implements RemoteEndpoint {

    private Gson gson = new Gson();

    @Override
    public String getRemoteEndpointKey() {
        return "default";
    }

    @Override
    public HttpEntity transformAlarmMessage(List<AlarmMessage> alarmMessage) {
        return new StringEntity(gson.toJson(alarmMessage), StandardCharsets.UTF_8);
    }
}
