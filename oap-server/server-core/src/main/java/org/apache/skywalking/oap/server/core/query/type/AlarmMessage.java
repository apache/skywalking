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

package org.apache.skywalking.oap.server.core.query.type;

import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.server.core.query.enumeration.Scope;
import org.apache.skywalking.oap.server.core.query.type.event.Event;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class AlarmMessage {
    private Scope scope;
    private int scopeId;
    private String id;
    private String message;
    private Long startTime;
    private transient String id1;
    private final List<KeyValue> tags;
    private List<Event> events = new ArrayList<>(2);

    public AlarmMessage() {
        tags = new ArrayList<>();
    }
}
