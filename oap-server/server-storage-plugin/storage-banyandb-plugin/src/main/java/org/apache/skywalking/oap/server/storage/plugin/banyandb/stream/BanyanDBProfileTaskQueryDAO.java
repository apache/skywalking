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

package org.apache.skywalking.oap.server.storage.plugin.banyandb.stream;

import com.google.common.collect.ImmutableList;
import org.apache.skywalking.banyandb.v1.client.RowEntity;
import org.apache.skywalking.banyandb.v1.client.StreamQuery;
import org.apache.skywalking.banyandb.v1.client.StreamQueryResponse;
import org.apache.skywalking.banyandb.v1.client.TagAndValue;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.profile.ProfileTaskRecord;
import org.apache.skywalking.oap.server.core.query.type.ProfileTask;
import org.apache.skywalking.oap.server.core.storage.profile.IProfileTaskQueryDAO;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBStorageClient;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.StreamMetaInfo;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * {@link org.apache.skywalking.oap.server.core.profile.ProfileTaskRecord} is a stream
 */
public class BanyanDBProfileTaskQueryDAO extends AbstractBanyanDBDAO implements IProfileTaskQueryDAO {
    public BanyanDBProfileTaskQueryDAO(BanyanDBStorageClient client) {
        super(client);
    }

    @Override
    public List<ProfileTask> getTaskList(String serviceId, String endpointName, Long startTimeBucket, Long endTimeBucket, Integer limit) throws IOException {
        StreamQueryResponse resp = query(ProfileTaskRecord.INDEX_NAME,
                ImmutableList.of(StreamMetaInfo.ID, ProfileTaskRecord.SERVICE_ID, ProfileTaskRecord.ENDPOINT_NAME,
                        ProfileTaskRecord.START_TIME, ProfileTaskRecord.DURATION, ProfileTaskRecord.MIN_DURATION_THRESHOLD,
                        ProfileTaskRecord.DUMP_PERIOD, ProfileTaskRecord.CREATE_TIME, ProfileTaskRecord.MAX_SAMPLING_COUNT), new QueryBuilder() {
                    @Override
                    public void apply(StreamQuery query) {
                        if (StringUtil.isNotEmpty(serviceId)) {
                            query.appendCondition(eq(ProfileTaskRecord.SERVICE_ID, serviceId));
                        }

                        if (StringUtil.isNotEmpty(endpointName)) {
                            query.appendCondition(eq(ProfileTaskRecord.ENDPOINT_NAME, endpointName));
                        }

                        if (Objects.nonNull(startTimeBucket)) {
                            query.appendCondition(gte(ProfileTaskRecord.START_TIME, TimeBucket.getTimestamp(startTimeBucket)));
                        }

                        if (Objects.nonNull(endTimeBucket)) {
                            query.appendCondition(lte(ProfileTaskRecord.START_TIME, TimeBucket.getTimestamp(endTimeBucket)));
                        }

                        if (Objects.nonNull(limit)) {
                            query.setLimit(limit);
                        }

                        query.setOrderBy(new StreamQuery.OrderBy(ProfileTaskRecord.START_TIME, StreamQuery.OrderBy.Type.DESC));
                    }
                });

        return resp.getElements().stream().map(new ProfileTaskDeserializer()).collect(Collectors.toList());
    }

    @Override
    public ProfileTask getById(String id) throws IOException {
        if (StringUtil.isEmpty(id)) {
            return null;
        }

        StreamQueryResponse resp = query(ProfileTaskRecord.INDEX_NAME,
                ImmutableList.of(StreamMetaInfo.ID, ProfileTaskRecord.SERVICE_ID, ProfileTaskRecord.ENDPOINT_NAME,
                        ProfileTaskRecord.START_TIME, ProfileTaskRecord.DURATION, ProfileTaskRecord.MIN_DURATION_THRESHOLD,
                        ProfileTaskRecord.DUMP_PERIOD, ProfileTaskRecord.CREATE_TIME, ProfileTaskRecord.MAX_SAMPLING_COUNT),
                new QueryBuilder() {
                    @Override
                    public void apply(StreamQuery query) {
                        query.appendCondition(eq(StreamMetaInfo.ID, id));
                        query.setLimit(1);
                    }
                });

        return resp.getElements().stream().map(new ProfileTaskDeserializer()).findAny().orElse(null);
    }

    public static class ProfileTaskDeserializer implements RowEntityDeserializer<ProfileTask> {
        @Override
        public ProfileTask apply(RowEntity row) {
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
            return profileTask;
        }
    }
}
