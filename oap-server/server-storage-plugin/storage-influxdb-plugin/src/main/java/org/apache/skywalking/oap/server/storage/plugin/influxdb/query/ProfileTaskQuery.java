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

package org.apache.skywalking.oap.server.storage.plugin.influxdb.query;

import com.google.common.collect.Lists;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import org.apache.skywalking.apm.util.StringUtil;
import org.apache.skywalking.oap.server.core.profile.ProfileTaskRecord;
import org.apache.skywalking.oap.server.core.query.entity.ProfileTask;
import org.apache.skywalking.oap.server.core.storage.profile.IProfileTaskQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.influxdb.InfluxClient;
import org.apache.skywalking.oap.server.storage.plugin.influxdb.InfluxModelConstants;
import org.apache.skywalking.oap.server.storage.plugin.influxdb.base.NoneStreamDAO;
import org.influxdb.dto.QueryResult;
import org.influxdb.querybuilder.SelectQueryImpl;
import org.influxdb.querybuilder.WhereQueryImpl;

import static org.influxdb.querybuilder.BuiltQuery.QueryBuilder.eq;
import static org.influxdb.querybuilder.BuiltQuery.QueryBuilder.gte;
import static org.influxdb.querybuilder.BuiltQuery.QueryBuilder.lte;
import static org.influxdb.querybuilder.BuiltQuery.QueryBuilder.select;

public class ProfileTaskQuery implements IProfileTaskQueryDAO {
    private InfluxClient client;

    public ProfileTaskQuery(InfluxClient client) {
        this.client = client;
    }

    @Override
    public List<ProfileTask> getTaskList(final Integer serviceId,
                                         final String endpointName,
                                         final Long startTimeBucket,
                                         final Long endTimeBucket,
                                         final Integer limit) throws IOException {
        WhereQueryImpl<SelectQueryImpl> query =
            select("id", ProfileTaskRecord.SERVICE_ID,
                   ProfileTaskRecord.ENDPOINT_NAME, ProfileTaskRecord.START_TIME,
                   ProfileTaskRecord.CREATE_TIME,
                   InfluxModelConstants.DURATION,
                   ProfileTaskRecord.MIN_DURATION_THRESHOLD,
                   ProfileTaskRecord.DUMP_PERIOD,
                   ProfileTaskRecord.MAX_SAMPLING_COUNT
            )
                .from(client.getDatabase(), ProfileTaskRecord.INDEX_NAME)
                .where();

        if (Objects.nonNull(serviceId)) {
            query.and(eq(NoneStreamDAO.TAG_SERVICE_ID, String.valueOf(serviceId)));
        }
        if (StringUtil.isNotEmpty(endpointName)) {
            query.and(eq(ProfileTaskRecord.ENDPOINT_NAME, endpointName));
        }
        if (Objects.nonNull(startTimeBucket)) {
            query.and(gte(ProfileTaskRecord.TIME_BUCKET, startTimeBucket));
        }
        if (Objects.nonNull(endTimeBucket)) {
            query.and(lte(ProfileTaskRecord.TIME_BUCKET, endTimeBucket));
        }
        if (Objects.nonNull(limit)) {
            query.limit(limit);
        }

        List<ProfileTask> tasks = Lists.newArrayList();
        QueryResult.Series series = client.queryForSingleSeries(query);
        if (series != null) {
            series.getValues().forEach(values -> {
                tasks.add(profileTaskBuilder(values));
            });
        }
        return tasks;
    }

    @Override
    public ProfileTask getById(final String id) throws IOException {
        if (StringUtil.isEmpty(id)) {
            return null;
        }
        SelectQueryImpl query = select("id", ProfileTaskRecord.SERVICE_ID,
                                       ProfileTaskRecord.ENDPOINT_NAME, ProfileTaskRecord.START_TIME,
                                       ProfileTaskRecord.CREATE_TIME,
                                       InfluxModelConstants.DURATION,
                                       ProfileTaskRecord.MIN_DURATION_THRESHOLD,
                                       ProfileTaskRecord.DUMP_PERIOD,
                                       ProfileTaskRecord.MAX_SAMPLING_COUNT
        )
            .from(client.getDatabase(), ProfileTaskRecord.INDEX_NAME)
            .where()
            .and(eq("id", id))
            .limit(1);

        QueryResult.Series series = client.queryForSingleSeries(query);
        if (Objects.nonNull(series)) {
            return profileTaskBuilder(series.getValues().get(0));
        }
        return null;
    }

    private static final ProfileTask profileTaskBuilder(List<Object> values) {
        return ProfileTask.builder()
                          .id((String) values.get(1))
                          .serviceId(((Number) values.get(2)).intValue())
                          .endpointName((String) values.get(3))
                          .startTime(((Number) values.get(4)).longValue())
                          .createTime(((Number) values.get(5)).longValue())
                          .duration((int) values.get(6))
                          .minDurationThreshold((int) values.get(7))
                          .dumpPeriod((int) values.get(8))
                          .maxSamplingCount((int) values.get(9))
                          .build();
    }

}
