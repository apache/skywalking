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

package org.apache.skywalking.oap.server.storage.plugin.banyandb.deserializer;

import com.google.common.collect.ImmutableList;
import org.apache.skywalking.banyandb.v1.client.RowEntity;
import org.apache.skywalking.banyandb.v1.client.TagAndValue;
import org.apache.skywalking.oap.server.core.query.type.event.EventType;
import org.apache.skywalking.oap.server.core.query.type.event.Source;
import org.apache.skywalking.oap.server.core.source.Event;

import java.util.List;

public class EventDeserializer extends AbstractBanyanDBDeserializer<org.apache.skywalking.oap.server.core.query.type.event.Event> {
    public EventDeserializer() {
        super(Event.INDEX_NAME,
                ImmutableList.of(Event.UUID, Event.SERVICE, Event.SERVICE_INSTANCE, Event.ENDPOINT, Event.NAME,
                        Event.TYPE, Event.START_TIME, Event.END_TIME),
                ImmutableList.of(Event.MESSAGE, Event.PARAMETERS));
    }

    @Override
    public org.apache.skywalking.oap.server.core.query.type.event.Event map(RowEntity row) {
        final org.apache.skywalking.oap.server.core.query.type.event.Event resultEvent = new org.apache.skywalking.oap.server.core.query.type.event.Event();
        // searchable
        final List<TagAndValue<?>> searchable = row.getTagFamilies().get(0);
        resultEvent.setUuid((String) searchable.get(0).getValue());
        resultEvent.setSource(new Source((String) searchable.get(1).getValue(), (String) searchable.get(2).getValue(), (String) searchable.get(3).getValue()));
        resultEvent.setName((String) searchable.get(4).getValue());
        resultEvent.setType(EventType.parse((String) searchable.get(5).getValue()));
        resultEvent.setStartTime(((Number) searchable.get(6).getValue()).longValue());
        resultEvent.setEndTime(((Number) searchable.get(7).getValue()).longValue());
        // data
        final List<TagAndValue<?>> data = row.getTagFamilies().get(1);
        resultEvent.setMessage((String) data.get(0).getValue());
        resultEvent.setParameters((String) data.get(1).getValue());
        return resultEvent;
    }
}
