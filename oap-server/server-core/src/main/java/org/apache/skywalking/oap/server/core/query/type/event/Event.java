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

package org.apache.skywalking.oap.server.core.query.type.event;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.Data;
import org.apache.skywalking.apm.util.StringUtil;
import org.apache.skywalking.oap.server.core.query.type.KeyValue;

@Data
public class Event {
    private static final Gson GSON = new Gson();

    private String uuid;

    private Source source;

    private String name;

    private EventType type;

    private String message;

    private List<KeyValue> parameters;

    private long startTime;

    private long endTime;

    public void setParameters(final List<KeyValue> parameters) {
        this.parameters = parameters;
    }

    public void setParameters(final String json) {
        if (StringUtil.isNotEmpty(json)) {
            final Map<String, String> map = GSON.fromJson(json, new TypeToken<Map<String, String>>() {
            }.getType());
            this.parameters = map.entrySet().stream().map(e -> new KeyValue(e.getKey(), e.getValue())).collect(Collectors.toList());
        }
    }

}
