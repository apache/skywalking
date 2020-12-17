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

package org.apache.skywalking.apm.testcase.vertxeventbus.util;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.MessageCodec;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;

public class CustomMessageCodec implements MessageCodec<CustomMessage, CustomMessage> {

    @Override
    public void encodeToWire(Buffer buffer, CustomMessage customMessage) {
        String jsonStr = Json.encode(customMessage);
        int length = jsonStr.getBytes().length;
        buffer.appendInt(length);
        buffer.appendString(jsonStr);
    }

    @Override
    public CustomMessage decodeFromWire(int position, Buffer buffer) {
        int length = buffer.getInt(position);
        JsonObject jsonMessage = new JsonObject(buffer.getString(position += 4, position + length));
        return new CustomMessage(jsonMessage.getString("message"));
    }

    @Override
    public CustomMessage transform(CustomMessage customMessage) {
        return customMessage;
    }

    @Override
    public String name() {
        return getClass().getSimpleName();
    }

    @Override
    public byte systemCodecID() {
        return -1;
    }
}
