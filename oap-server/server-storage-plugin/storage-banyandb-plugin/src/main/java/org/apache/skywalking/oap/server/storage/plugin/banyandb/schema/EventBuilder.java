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

package org.apache.skywalking.oap.server.storage.plugin.banyandb.schema;

import com.google.common.collect.ImmutableList;
import org.apache.skywalking.banyandb.v1.Banyandb;
import org.apache.skywalking.banyandb.v1.client.SerializableTag;
import org.apache.skywalking.banyandb.v1.client.TagAndValue;
import org.apache.skywalking.oap.server.core.source.Event;

import java.util.ArrayList;
import java.util.List;

public class EventBuilder extends BanyanDBStorageDataBuilder<Event> {
    @Override
    protected List<SerializableTag<Banyandb.TagValue>> searchableTags(Event entity) {
        List<SerializableTag<Banyandb.TagValue>> searchable = new ArrayList<>(8);
        searchable.add(TagAndValue.stringField(entity.getUuid()));
        searchable.add(TagAndValue.stringField(entity.getService()));
        searchable.add(TagAndValue.stringField(entity.getServiceInstance()));
        searchable.add(TagAndValue.stringField(entity.getEndpoint()));
        searchable.add(TagAndValue.stringField(entity.getName()));
        searchable.add(TagAndValue.stringField(entity.getType()));
        searchable.add(TagAndValue.longField(entity.getStartTime()));
        searchable.add(TagAndValue.longField(entity.getEndTime()));
        return searchable;
    }

    @Override
    protected List<SerializableTag<Banyandb.TagValue>> dataTags(Event entity) {
        return ImmutableList.of(
                TagAndValue.stringField(entity.getMessage()),
                TagAndValue.stringField(entity.getParameters())
        );
    }
}
