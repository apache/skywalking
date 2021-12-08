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
import org.apache.skywalking.oap.server.core.alarm.AlarmRecord;

import java.util.ArrayList;
import java.util.List;

public class AlarmRecordBuilder extends BanyanDBStorageDataBuilder<AlarmRecord> {
    public static final List<String> INDEXED_TAGS = ImmutableList.of(
            "level"
    );

    @Override
    protected List<SerializableTag<Banyandb.TagValue>> searchableTags(AlarmRecord entity) {
        List<SerializableTag<Banyandb.TagValue>> searchable = new ArrayList<>(2);
        searchable.add(TagAndValue.longField(entity.getScope()));
        searchable.add(TagAndValue.longField(entity.getStartTime()));
        searchable.addAll(filterSearchableTags(entity.getTags(), INDEXED_TAGS));
        return searchable;
    }

    @Override
    protected List<SerializableTag<Banyandb.TagValue>> dataTags(AlarmRecord entity) {
        List<SerializableTag<Banyandb.TagValue>> data = new ArrayList<>(6);
        data.add(TagAndValue.stringField(entity.getName()));
        data.add(TagAndValue.stringField(entity.getId0()));
        data.add(TagAndValue.stringField(entity.getId1()));
        data.add(TagAndValue.stringField(entity.getAlarmMessage()));
        data.add(TagAndValue.stringField(entity.getRuleName()));
        data.add(TagAndValue.binaryField(entity.getTagsRawData()));
        return data;
    }
}
