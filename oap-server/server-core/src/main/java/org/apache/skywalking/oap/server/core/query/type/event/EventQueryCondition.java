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

import lombok.Data;
import org.apache.skywalking.oap.server.core.query.enumeration.Order;
import org.apache.skywalking.oap.server.core.query.input.Duration;

import static org.apache.skywalking.oap.server.core.storage.query.IEventQueryDAO.DEFAULT_SIZE;
import static org.apache.skywalking.oap.server.core.storage.query.IEventQueryDAO.MAX_SIZE;

@Data
public class EventQueryCondition {
    private String uuid;

    private Source source;

    private String name;

    private EventType type;

    private Duration time;

    private Order order;

    private int size;

    public int getSize() {
        return size > 0 ? Math.min(size, MAX_SIZE) : DEFAULT_SIZE;
    }
}
