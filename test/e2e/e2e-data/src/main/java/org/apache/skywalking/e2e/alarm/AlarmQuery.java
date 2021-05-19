/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.skywalking.e2e.alarm;

import org.apache.skywalking.e2e.AbstractQuery;
import org.apache.skywalking.e2e.event.Event;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

public class AlarmQuery extends AbstractQuery<AlarmQuery> {
    private List<Map<String, String>> tags = Collections.emptyList();

    private List<Event> events = Collections.emptyList();

    public List<Map<String, String>> tags() {
        return tags;
    }

    public List<Event> events() {
        return events;
    }

    public AlarmQuery tags(List<Map<String, String>> tags) {
        this.tags = tags;
        return this;
    }

    public AlarmQuery events(List<Event> events) {
        this.events = events;
        return this;
    }

    public AlarmQuery addTag(String key, String value) {
        if (Collections.EMPTY_LIST.equals(tags)) {
            tags = new ArrayList<>();
        }
        Map<String, String> tag = new HashMap<>();
        tag.put("key", key);
        tag.put("value", value);
        tags.add(tag);
        return this;
    }

    public AlarmQuery addEvents(List<Event> events) {
        if (Collections.EMPTY_LIST.equals(events)) {
            events = new ArrayList<>();
        }

        events.addAll(events);
        return this;
    }
}
