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

import org.apache.skywalking.banyandb.v1.client.PairQueryCondition;
import org.apache.skywalking.banyandb.v1.client.StreamQuery;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.profile.ProfileTaskRecord;
import org.apache.skywalking.oap.server.core.query.type.ProfileTask;
import org.apache.skywalking.oap.server.core.storage.profile.IProfileTaskQueryDAO;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBStorageClient;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.deserializer.ProfileTaskDeserializer;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

/**
 * {@link org.apache.skywalking.oap.server.core.profile.ProfileTaskRecord} is a stream
 */
public class BanyanDBProfileTaskQueryDAO extends AbstractBanyanDBDAO implements IProfileTaskQueryDAO {
    public BanyanDBProfileTaskQueryDAO(BanyanDBStorageClient client) {
        super(client);
    }

    @Override
    public List<ProfileTask> getTaskList(String serviceId, String endpointName, Long startTimeBucket, Long endTimeBucket, Integer limit) throws IOException {
        return query(ProfileTask.class, new QueryBuilder() {
            @Override
            public void apply(StreamQuery query) {
                if (StringUtil.isNotEmpty(serviceId)) {
                    query.appendCondition(PairQueryCondition.StringQueryCondition.eq("searchable",
                            ProfileTaskRecord.SERVICE_ID, serviceId));
                }

                if (StringUtil.isNotEmpty(endpointName)) {
                    query.appendCondition(PairQueryCondition.StringQueryCondition.eq("searchable",
                            ProfileTaskRecord.ENDPOINT_NAME, endpointName));
                }

                if (Objects.nonNull(startTimeBucket)) {
                    query.appendCondition(PairQueryCondition.LongQueryCondition.ge("searchable",
                            ProfileTaskRecord.START_TIME, TimeBucket.getTimestamp(startTimeBucket)));
                }

                if (Objects.nonNull(endTimeBucket)) {
                    query.appendCondition(PairQueryCondition.LongQueryCondition.le("searchable",
                            ProfileTaskRecord.START_TIME, TimeBucket.getTimestamp(endTimeBucket)));
                }

                if (Objects.nonNull(limit)) {
                    query.setLimit(limit);
                }

                query.setOrderBy(new StreamQuery.OrderBy(ProfileTaskRecord.START_TIME, StreamQuery.OrderBy.Type.DESC));
            }
        });
    }

    @Override
    public ProfileTask getById(String id) throws IOException {
        if (StringUtil.isEmpty(id)) {
            return null;
        }

        return query(ProfileTask.class, new QueryBuilder() {
            @Override
            public void apply(StreamQuery query) {
                query.appendCondition(PairQueryCondition.StringQueryCondition.eq("searchable", ProfileTaskDeserializer.ID, id));
                query.setLimit(1);
            }
        }).stream().findAny().orElse(null);
    }
}
