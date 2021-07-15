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

package org.apache.skywalking.oap.server.storage.plugin.iotdb.profile;

import lombok.extern.slf4j.Slf4j;
import org.apache.iotdb.rpc.IoTDBConnectionException;
import org.apache.iotdb.rpc.StatementExecutionException;
import org.apache.iotdb.session.pool.SessionDataSetWrapper;
import org.apache.iotdb.session.pool.SessionPool;
import org.apache.iotdb.tsfile.read.common.Field;
import org.apache.iotdb.tsfile.read.common.RowRecord;
import org.apache.skywalking.apm.util.StringUtil;
import org.apache.skywalking.oap.server.core.profile.ProfileTaskRecord;
import org.apache.skywalking.oap.server.core.query.type.ProfileTask;
import org.apache.skywalking.oap.server.core.storage.profile.IProfileTaskQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.iotdb.IoTDBClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
public class IoTDBProfileTaskQueryDAO implements IProfileTaskQueryDAO {
    private final IoTDBClient client;

    public IoTDBProfileTaskQueryDAO(IoTDBClient client) {
        this.client = client;
    }

    @Override
    public List<ProfileTask> getTaskList(String serviceId, String endpointName, Long startTimeBucket,
                                         Long endTimeBucket, Integer limit) throws IOException {
        StringBuilder devicePath = new StringBuilder();
        devicePath.append(client.getStorageGroup()).append(IoTDBClient.DOT).append(ProfileTaskRecord.INDEX_NAME);

        final StringBuilder query = new StringBuilder();
        query.append("select * from ").append(devicePath).append(" where 1=1");
        if (StringUtil.isNotEmpty(serviceId)) {
            query.append(ProfileTaskRecord.SERVICE_ID).append(" = '").append(serviceId).append("'");
        }
        if (StringUtil.isNotEmpty(endpointName)) {
            query.append(" and ").append(ProfileTaskRecord.ENDPOINT_NAME).append(" = ").append(endpointName);
        }
        if (Objects.nonNull(startTimeBucket)) {
            query.append(" and ").append(ProfileTaskRecord.TIME_BUCKET).append(" >= ").append(startTimeBucket);
        }
        if (Objects.nonNull(endTimeBucket)) {
            query.append(" and ").append(ProfileTaskRecord.TIME_BUCKET).append(" <= ").append(endTimeBucket);
        }
        if (Objects.nonNull(limit)) {
            query.append(" limit ").append(limit);
        }

        SessionPool pool = client.getSessionPool();
        SessionDataSetWrapper wrapper;
        final List<ProfileTask> profileTaskList = new ArrayList<>();
        try {
            if (!pool.checkTimeseriesExists(devicePath.toString())) {
                return profileTaskList;
            }
            wrapper = pool.executeQueryStatement(query.toString());
            if (log.isDebugEnabled()) {
                log.debug("SQL: {} result: {}", query, wrapper);
            }
            List<String> columnNames = wrapper.getColumnNames();
            while (wrapper.hasNext()) {
                RowRecord rowRecord = wrapper.next();
                Map<String, Field> map = new HashMap<>();
                for (int i = 0; i < columnNames.size(); i++) {
                    map.put(columnNames.get(i), rowRecord.getFields().get(i));
                }
                profileTaskList.add(profileTaskBuilder(map));
            }
            pool.closeResultSet(wrapper);
            return profileTaskList;
        } catch (IoTDBConnectionException | StatementExecutionException e) {
            e.printStackTrace();
            throw new IOException(e);
        }
    }

    @Override
    public ProfileTask getById(String id) throws IOException {
        StringBuilder devicePath = new StringBuilder();
        devicePath.append(client.getStorageGroup()).append(IoTDBClient.DOT).append(ProfileTaskRecord.INDEX_NAME);

        final StringBuilder query = new StringBuilder();
        query.append("select * from ").append(ProfileTaskRecord.INDEX_NAME).append(" where id = '")
                .append(id).append("' limit 1");

        SessionPool pool = client.getSessionPool();
        SessionDataSetWrapper wrapper;
        try {
            if (!pool.checkTimeseriesExists(devicePath.toString())) {
                return null;
            }
            wrapper = pool.executeQueryStatement(query.toString());
            if (log.isDebugEnabled()) {
                log.debug("SQL: {} result: {}", query, wrapper);
            }
            List<String> columnNames = wrapper.getColumnNames();
            if (wrapper.hasNext()) {
                RowRecord rowRecord = wrapper.next();
                Map<String, Field> map = new HashMap<>();
                for (int i = 0; i < rowRecord.getFields().size(); i++) {
                    map.put(columnNames.get(i), rowRecord.getFields().get(i));
                }
                pool.closeResultSet(wrapper);
                return profileTaskBuilder(map);
            }
            return null;
        } catch (IoTDBConnectionException | StatementExecutionException e) {
            e.printStackTrace();
            throw new IOException(e);
        }
    }

    // TODO why ProfileTask don't implement StorageData and create a StorageHashMapBuilder?
    // TODO could use ProfileTaskRecord to reduce code size
    private static ProfileTask profileTaskBuilder(Map<String, Field> map) {
        return ProfileTask.builder()
                .id(map.get("id").getStringValue())
                .serviceId(map.get("serviceId").getStringValue())
                .endpointName(map.get("endpointName").getStringValue())
                .startTime(map.get("startTime").getLongV())
                .createTime(map.get("createTime").getLongV())
                .duration(map.get("duration").getIntV())
                .minDurationThreshold(map.get("minDurationThreshold").getIntV())
                .dumpPeriod(map.get("dumpPeriod").getIntV())
                .maxSamplingCount(map.get("maxSamplingCount").getIntV())
                .build();
    }
}
