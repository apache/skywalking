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

import org.apache.skywalking.banyandb.model.v1.BanyandbModel;
import org.apache.skywalking.banyandb.v1.client.SerializableTag;
import org.apache.skywalking.banyandb.v1.client.TagAndValue;
import org.apache.skywalking.oap.server.core.profile.ProfileTaskLogRecord;

import java.util.ArrayList;
import java.util.List;

public class ProfileTaskLogRecordBuilder extends BanyanDBStorageDataBuilder<ProfileTaskLogRecord> {
    @Override
    protected List<SerializableTag<BanyandbModel.TagValue>> searchableTags(ProfileTaskLogRecord entity) {
        List<SerializableTag<BanyandbModel.TagValue>> searchable = new ArrayList<>(2);
        searchable.add(TagAndValue.longField(entity.getOperationTime()));
        searchable.add(TagAndValue.stringField(entity.getInstanceId()));
        return searchable;
    }

    @Override
    protected List<SerializableTag<BanyandbModel.TagValue>> dataTags(ProfileTaskLogRecord entity) {
        List<SerializableTag<BanyandbModel.TagValue>> data = new ArrayList<>(2);
        data.add(TagAndValue.stringField(entity.getTaskId()));
        data.add(TagAndValue.longField(entity.getOperationType()));
        return data;
    }
}
