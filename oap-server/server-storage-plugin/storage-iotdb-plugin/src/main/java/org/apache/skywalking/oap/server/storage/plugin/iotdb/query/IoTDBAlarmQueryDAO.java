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

package org.apache.skywalking.oap.server.storage.plugin.iotdb.query;

import org.apache.skywalking.oap.server.core.alarm.AlarmRecord;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.Tag;
import org.apache.skywalking.oap.server.core.query.type.Alarms;
import org.apache.skywalking.oap.server.core.storage.query.IAlarmQueryDAO;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.storage.plugin.iotdb.IoTDBClient;
import org.elasticsearch.common.Strings;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

public class IoTDBAlarmQueryDAO implements IAlarmQueryDAO {
    private final IoTDBClient client;

    public IoTDBAlarmQueryDAO(IoTDBClient client) {
        this.client = client;
    }

    @Override
    public Alarms getAlarm(Integer scopeId, String keyword, int limit, int from, long startTB, long endTB, List<Tag> tags) throws IOException {
        StringBuilder query = new StringBuilder();
        query.append("select top_k(").append(AlarmRecord.START_TIME).append(", 'k'='")
                .append(limit + from).append("') from ")
                .append(client.getStorageGroup()).append(IoTDBClient.DOT).append(AlarmRecord.INDEX_NAME);

        query.append("select *, ");
        if (!Strings.isNullOrEmpty(keyword)) {
            query.append(", string_contains(").append(AlarmRecord.ALARM_MESSAGE).append(", 's'='").append(keyword).append("')");
        }
        query.append(" from ").append(client.getStorageGroup()).append(IoTDBClient.DOT).append(AlarmRecord.INDEX_NAME)
                .append(" where 1=1 ");
        if (Objects.nonNull(scopeId)) {
            query.append(" and ").append(AlarmRecord.SCOPE).append(" = '").append(scopeId).append("'");
        }
        if (startTB != 0 && endTB != 0) {
            query.append(" and ").append(AlarmRecord.TIME_BUCKET).append(" >= ").append(startTB);
            query.append(" and ").append(AlarmRecord.TIME_BUCKET).append(" <= ").append(endTB);
        }

        if (CollectionUtils.isNotEmpty(tags)) {
            //TODO How to deal with tags
        }

        return null;
    }
}
