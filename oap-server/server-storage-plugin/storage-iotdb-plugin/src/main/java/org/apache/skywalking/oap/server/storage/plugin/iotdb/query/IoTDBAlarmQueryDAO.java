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

import com.google.common.base.Strings;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.apache.skywalking.oap.server.core.alarm.AlarmRecord;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.Tag;
import org.apache.skywalking.oap.server.core.query.enumeration.Scope;
import org.apache.skywalking.oap.server.core.query.type.AlarmMessage;
import org.apache.skywalking.oap.server.core.query.type.Alarms;
import org.apache.skywalking.oap.server.core.storage.StorageData;
import org.apache.skywalking.oap.server.core.storage.StorageHashMapBuilder;
import org.apache.skywalking.oap.server.core.storage.query.IAlarmQueryDAO;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.storage.plugin.iotdb.IoTDBClient;

@RequiredArgsConstructor
public class IoTDBAlarmQueryDAO implements IAlarmQueryDAO {
    private final IoTDBClient client;
    private final StorageHashMapBuilder<AlarmRecord> storageBuilder = new AlarmRecord.Builder();

    @Override
    public Alarms getAlarm(Integer scopeId, String keyword, int limit, int from, long startTB, long endTB, List<Tag> tags) throws IOException {
        StringBuilder query = new StringBuilder();
        // This method maybe have poor efficiency. It queries all data which meets a condition without select function.
        // https://github.com/apache/iotdb/discussions/3888
        query.append("select * from ");
        query = client.addModelPath(query, AlarmRecord.INDEX_NAME);
        query = client.addQueryAsterisk(AlarmRecord.INDEX_NAME, query);
        query.append(" where 1=1");
        if (Objects.nonNull(scopeId)) {
            query.append(" and ").append(AlarmRecord.SCOPE).append(" = ").append(scopeId);
        }
        if (startTB != 0 && endTB != 0) {
            query.append(" and ").append(IoTDBClient.TIME).append(" >= ").append(TimeBucket.getTimestamp(startTB));
            query.append(" and ").append(IoTDBClient.TIME).append(" <= ").append(TimeBucket.getTimestamp(endTB));
        }
        if (!Strings.isNullOrEmpty(keyword)) {
            query.append(" and ").append(AlarmRecord.ALARM_MESSAGE).append(" like '%").append(keyword).append("%'");
        }
        if (CollectionUtils.isNotEmpty(tags)) {
            for (final Tag tag : tags) {
                query.append(" and ").append(tag.getKey()).append(" = \"").append(tag.getValue()).append("\"");
            }
        }
        // IoTDB doesn't support the query contains "1=1" and "*" at the meantime.
        String queryString = query.toString().replace("1=1 and ", "");
        queryString = queryString + IoTDBClient.ALIGN_BY_DEVICE;

        Alarms alarms = new Alarms();
        List<? super StorageData> storageDataList = client.filterQuery(AlarmRecord.INDEX_NAME, queryString, storageBuilder);
        int limitCount = 0;
        for (int i = 0; i < storageDataList.size(); i++) {
            if (i >= from && limitCount < limit) {
                limitCount++;
                AlarmRecord alarmRecord = (AlarmRecord) storageDataList.get(i);
                alarms.getMsgs().add(parseMessage(alarmRecord));
            }
        }
        alarms.setTotal(storageDataList.size());
        // resort by self, because of the select query result order by time.
        alarms.getMsgs().sort((AlarmMessage m1, AlarmMessage m2) -> Long.compare(m2.getStartTime(), m1.getStartTime()));
        return alarms;
    }

    private AlarmMessage parseMessage(AlarmRecord alarmRecord) {
        AlarmMessage message = new AlarmMessage();
        message.setId(alarmRecord.getId0());
        message.setId1(alarmRecord.getId1());
        message.setMessage(alarmRecord.getAlarmMessage());
        message.setStartTime(alarmRecord.getStartTime());
        message.setScope(Scope.Finder.valueOf(alarmRecord.getScope()));
        message.setScopeId(alarmRecord.getScope());
        if (!CollectionUtils.isEmpty(alarmRecord.getTagsRawData())) {
            parserDataBinary(alarmRecord.getTagsRawData(), message.getTags());
        }
        return message;
    }
}
