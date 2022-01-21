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
import org.apache.skywalking.oap.server.core.profile.ProfileTaskRecord;

import java.util.ArrayList;
import java.util.List;

public class ProfileTaskRecordBuilder extends BanyanDBStorageDataBuilder<ProfileTaskRecord> {
    @Override
    protected List<SerializableTag<BanyandbModel.TagValue>> searchableTags(ProfileTaskRecord entity) {
        List<SerializableTag<BanyandbModel.TagValue>> searchable = new ArrayList<>(9);
        // 0 - id
        searchable.add(TagAndValue.stringField(entity.id()));
        // 1 - service_id
        searchable.add(TagAndValue.stringField(entity.getServiceId()));
        // 2 - endpoint_name
        searchable.add(TagAndValue.stringField(entity.getEndpointName()));
        // 3 - start_time
        searchable.add(TagAndValue.longField(entity.getStartTime()));
        // 4 - duration
        searchable.add(TagAndValue.longField(entity.getDuration()));
        // 5 - min_duration_threshold
        searchable.add(TagAndValue.longField(entity.getMinDurationThreshold()));
        // 6 - dump_period
        searchable.add(TagAndValue.longField(entity.getDumpPeriod()));
        // 7 - create_time
        searchable.add(TagAndValue.longField(entity.getCreateTime()));
        // 8 - max_sampling_count
        searchable.add(TagAndValue.longField(entity.getMaxSamplingCount()));
        return searchable;
    }
}
