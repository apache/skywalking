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
import org.apache.skywalking.oap.server.core.storage.query.IAlarmQueryDAO;
import org.apache.skywalking.oap.server.core.storage.type.StorageBuilder;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.storage.plugin.iotdb.IoTDBClient;

@RequiredArgsConstructor
public class IoTDBAlarmQueryDAO implements IAlarmQueryDAO {
    private final IoTDBClient client;
    private final StorageBuilder<AlarmRecord> storageBuilder = new AlarmRecord.Builder();

    @Override
    public Alarms getAlarm(Integer scopeId, String keyword, int limit, int from, long startTB, long endTB, List<Tag> tags) throws IOException {
        StringBuilder query = new StringBuilder();
        // This method maybe have poor efficiency. It queries all data which meets a condition without select function.
        // https://github.com/apache/iotdb/discussions/3888
        query.append("select * from ");
        query = client.addModelPath(query, AlarmRecord.INDEX_NAME);
        query = client.addQueryAsterisk(AlarmRecord.INDEX_NAME, query);

        StringBuilder where = new StringBuilder(" where ");
        if (Objects.nonNull(scopeId)) {
            where.append(AlarmRecord.SCOPE).append(" = ").append(scopeId).append(" and ");
        }
        if (startTB != 0 && endTB != 0) {
            where.append(IoTDBClient.TIME).append(" >= ").append(TimeBucket.getTimestamp(startTB)).append(" and ");
            where.append(IoTDBClient.TIME).append(" <= ").append(TimeBucket.getTimestamp(endTB)).append(" and ");
        }
        if (!Strings.isNullOrEmpty(keyword)) {
            where.append(AlarmRecord.ALARM_MESSAGE).append(" like '%").append(keyword).append("%'").append(" and ");
        }
        if (CollectionUtils.isNotEmpty(tags)) {
            for (final Tag tag : tags) {
                where.append(tag.getKey()).append(" = \"").append(tag.getValue()).append("\"").append(" and ");
            }
        }
        if (where.length() > 7) {
            int length = where.length();
            where.delete(length - 5, length);
            query.append(where);
        }
        query.append(IoTDBClient.ALIGN_BY_DEVICE);

        Alarms alarms = new Alarms();
        List<? super StorageData> storageDataList = client.filterQuery(AlarmRecord.INDEX_NAME, query.toString(), storageBuilder);
        int limitCount = 0;
        for (int i = from; i < storageDataList.size(); i++) {
            if (limitCount < limit) {
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
