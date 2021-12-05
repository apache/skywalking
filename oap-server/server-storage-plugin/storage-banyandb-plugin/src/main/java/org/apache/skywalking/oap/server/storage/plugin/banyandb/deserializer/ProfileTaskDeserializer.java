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
import org.apache.skywalking.oap.server.core.profile.ProfileTaskRecord;
import org.apache.skywalking.oap.server.core.query.type.ProfileTask;

import java.util.List;

public class ProfileTaskDeserializer extends AbstractBanyanDBDeserializer<ProfileTask> {
    public static final String ID = "profile_task_query_id";

    public ProfileTaskDeserializer() {
        super(ProfileTaskRecord.INDEX_NAME,
                ImmutableList.of(ID, ProfileTaskRecord.SERVICE_ID, ProfileTaskRecord.ENDPOINT_NAME,
                        ProfileTaskRecord.START_TIME, ProfileTaskRecord.DURATION, ProfileTaskRecord.MIN_DURATION_THRESHOLD,
                        ProfileTaskRecord.DUMP_PERIOD, ProfileTaskRecord.CREATE_TIME, ProfileTaskRecord.MAX_SAMPLING_COUNT));
    }

    @Override
    public ProfileTask map(RowEntity row) {
        ProfileTask profileTask = new ProfileTask();
        final List<TagAndValue<?>> searchable = row.getTagFamilies().get(0);
        profileTask.setId((String) searchable.get(0).getValue());
        profileTask.setServiceId((String) searchable.get(1).getValue());
        profileTask.setEndpointName((String) searchable.get(2).getValue());
        profileTask.setStartTime(((Number) searchable.get(3).getValue()).longValue());
        profileTask.setDuration(((Number) searchable.get(4).getValue()).intValue());
        profileTask.setMinDurationThreshold(((Number) searchable.get(5).getValue()).intValue());
        profileTask.setDumpPeriod(((Number) searchable.get(6).getValue()).intValue());
        profileTask.setCreateTime(((Number) searchable.get(7).getValue()).intValue());
        profileTask.setMaxSamplingCount(((Number) searchable.get(8).getValue()).intValue());
        return null;
    }
}
