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
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.util.StringUtil;
import org.apache.skywalking.oap.server.core.profile.ProfileTaskRecord;
import org.apache.skywalking.oap.server.core.query.type.ProfileTask;
import org.apache.skywalking.oap.server.core.storage.profile.IProfileTaskQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.influxdb.InfluxClient;
import org.apache.skywalking.oap.server.storage.plugin.influxdb.InfluxConstants;
import org.influxdb.dto.QueryResult;
import org.influxdb.querybuilder.SelectQueryImpl;
import org.influxdb.querybuilder.WhereQueryImpl;

import static org.influxdb.querybuilder.BuiltQuery.QueryBuilder.eq;
import static org.influxdb.querybuilder.BuiltQuery.QueryBuilder.gte;
import static org.influxdb.querybuilder.BuiltQuery.QueryBuilder.lte;
import static org.influxdb.querybuilder.BuiltQuery.QueryBuilder.select;

@Slf4j
public class ProfileTaskQuery implements IProfileTaskQueryDAO {
    private final InfluxClient client;

    public ProfileTaskQuery(InfluxClient client) {
        this.client = client;
    }

    @Override
    public List<ProfileTask> getTaskList(final String serviceId,
                                         final String endpointName,
                                         final Long startTimeBucket,
                                         final Long endTimeBucket,
                                         final Integer limit) throws IOException {
        final WhereQueryImpl<SelectQueryImpl> query =
            select(
                InfluxConstants.ID_COLUMN,
                ProfileTaskRecord.SERVICE_ID,
                ProfileTaskRecord.ENDPOINT_NAME,
                ProfileTaskRecord.START_TIME,
                ProfileTaskRecord.CREATE_TIME,
                InfluxConstants.DURATION,
                ProfileTaskRecord.MIN_DURATION_THRESHOLD,
                ProfileTaskRecord.DUMP_PERIOD,
                ProfileTaskRecord.MAX_SAMPLING_COUNT
            )
                .from(client.getDatabase(), ProfileTaskRecord.INDEX_NAME)
                .where();

        if (StringUtil.isNotEmpty(serviceId)) {
            query.and(eq(InfluxConstants.TagName.SERVICE_ID, serviceId));
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

        final List<ProfileTask> tasks = Lists.newArrayList();
        QueryResult.Series series = client.queryForSingleSeries(query);
        if (log.isDebugEnabled()) {
            log.debug("SQL: {} result: {}", query.getCommand(), series);
        }
        if (series != null) {
            series.getValues().forEach(values -> tasks.add(profileTaskBuilder(values)));
        }
        return tasks;
    }

    @Override
    public ProfileTask getById(final String id) throws IOException {
        if (StringUtil.isEmpty(id)) {
            return null;
        }
        final SelectQueryImpl query = select(
            InfluxConstants.ID_COLUMN,
            ProfileTaskRecord.SERVICE_ID,
            ProfileTaskRecord.ENDPOINT_NAME,
            ProfileTaskRecord.START_TIME,
            ProfileTaskRecord.CREATE_TIME,
            InfluxConstants.DURATION,
            ProfileTaskRecord.MIN_DURATION_THRESHOLD,
            ProfileTaskRecord.DUMP_PERIOD,
            ProfileTaskRecord.MAX_SAMPLING_COUNT
        )
            .from(client.getDatabase(), ProfileTaskRecord.INDEX_NAME)
            .where()
            .and(eq(InfluxConstants.ID_COLUMN, id))
            .limit(1);

        final QueryResult.Series series = client.queryForSingleSeries(query);
        if (log.isDebugEnabled()) {
            log.debug("SQL: {} result: {}", query.getCommand(), series);
        }
        if (Objects.nonNull(series)) {
            return profileTaskBuilder(series.getValues().get(0));
        }
        return null;
    }

    private static ProfileTask profileTaskBuilder(List<Object> values) {
        return ProfileTask.builder()
                          .id((String) values.get(1))
                          .serviceId((String) values.get(2))
                          .endpointName((String) values.get(3))
                          .startTime(((Number) values.get(4)).longValue())
                          .createTime(((Number) values.get(5)).longValue())
                          .duration(((Number) values.get(6)).intValue())
                          .minDurationThreshold(((Number) values.get(7)).intValue())
                          .dumpPeriod(((Number) values.get(8)).intValue())
                          .maxSamplingCount(((Number) values.get(9)).intValue())
                          .build();
    }

}
