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
import com.google.common.collect.Maps;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.apache.skywalking.apm.util.StringUtil;
import org.apache.skywalking.oap.server.core.profile.ProfileTaskNoneStream;
import org.apache.skywalking.oap.server.core.query.entity.ProfileTask;
import org.apache.skywalking.oap.server.core.storage.profile.IProfileTaskQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.influxdb.InfluxClient;
import org.influxdb.dto.QueryResult;
import org.influxdb.querybuilder.SelectQueryImpl;
import org.influxdb.querybuilder.WhereQueryImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.influxdb.querybuilder.BuiltQuery.QueryBuilder.eq;
import static org.influxdb.querybuilder.BuiltQuery.QueryBuilder.gte;
import static org.influxdb.querybuilder.BuiltQuery.QueryBuilder.select;

public class ProfileTaskQuery implements IProfileTaskQueryDAO {
    private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private final InfluxClient client;

    public ProfileTaskQuery(InfluxClient client) {
        this.client = client;
    }

    @Override
    public List<ProfileTask> getTaskList(Integer serviceId, String endpointName, Long startTimeBucket,
        Long endTimeBucket, Integer limit) throws IOException {
        WhereQueryImpl<SelectQueryImpl> query = select()
            .function("top", ProfileTaskNoneStream.START_TIME, Math.max(10, limit))
            .all()
            .from(client.getDatabase(), ProfileTaskNoneStream.INDEX_NAME)
            .where();
        if (Objects.nonNull(serviceId)) {
            query.and(eq(ProfileTaskNoneStream.SERVICE_ID, serviceId));
        }
        if (StringUtil.isNotEmpty(endpointName)) {
            query.and(eq(ProfileTaskNoneStream.ENDPOINT_NAME, endpointName));
        }
        if (Objects.nonNull(startTimeBucket)) {
            query.and(gte(InfluxClient.TIME, InfluxClient.timeInterval(startTimeBucket)));
        }
        if (Objects.nonNull(endTimeBucket)) {
            query.and(gte(InfluxClient.TIME, InfluxClient.timeInterval(endTimeBucket)));
        }

        List<QueryResult.Series> series = client.queryForSeries(query);
        if (LOG.isDebugEnabled()) {
            LOG.debug("SQL: {} \nresult set: {}", query.getCommand(), series);
        }
        if (series == null || series.isEmpty()) {
            return Collections.emptyList();
        }

        QueryResult.Series dataset = series.get(0);
        Map<String, Integer> columnsMap = Maps.newHashMap();
        List<String> columns = dataset.getColumns();
        for (int i = 0; i < columns.size(); i++) {
            columnsMap.put(columns.get(i), i);
        }

        List<ProfileTask> tasks = Lists.newArrayListWithCapacity(dataset.getValues().size());
        dataset.getValues().forEach(values -> {
            tasks.add(profileTaskBuilder(values, columnsMap));
        });
        return tasks;
    }

    @Override public ProfileTask getById(String id) throws IOException {
        SelectQueryImpl query = select().all()
            .from(client.getDatabase(), ProfileTaskNoneStream.INDEX_NAME)
            .where(eq(ProfileTaskNoneStream.SERVICE_ID, id))
            .limit(1);
        List<QueryResult.Series> series = client.queryForSeries(query);
        if (LOG.isDebugEnabled()) {
            LOG.debug("SQL: {} \nresult set: {}", query.getCommand(), series);
        }
        if (series == null || series.isEmpty()) {
            return null;
        }
        List<String> columns = series.get(0).getColumns();
        Map<String, Integer> columnsMap = Maps.newHashMap();
        for (int i = 0; i < columns.size(); i++) {
            columnsMap.put(columns.get(i), i);
        }

        return profileTaskBuilder(series.get(0).getValues().get(0), columnsMap);
    }

    private static final ProfileTask profileTaskBuilder(List<Object> values, Map<String, Integer> columnsMap) {
        return ProfileTask.builder()
            .id((String)values.get(columnsMap.get("id")))
            .serviceId((int)values.get(columnsMap.get(ProfileTaskNoneStream.SERVICE_ID)))
            .endpointName((String)values.get(columnsMap.get(ProfileTaskNoneStream.ENDPOINT_NAME)))
            .startTime((long)values.get(columnsMap.get(ProfileTaskNoneStream.START_TIME)))
            .createTime((long)values.get(columnsMap.get(ProfileTaskNoneStream.CREATE_TIME)))
            .duration((int)values.get(columnsMap.get(ProfileTaskNoneStream.DURATION)))
            .minDurationThreshold((int)values.get(columnsMap.get(ProfileTaskNoneStream.MIN_DURATION_THRESHOLD)))
            .dumpPeriod((int)values.get(columnsMap.get(ProfileTaskNoneStream.DUMP_PERIOD)))
            .maxSamplingCount((int)values.get(columnsMap.get(ProfileTaskNoneStream.MAX_SAMPLING_COUNT)))
            .build();
    }

}
