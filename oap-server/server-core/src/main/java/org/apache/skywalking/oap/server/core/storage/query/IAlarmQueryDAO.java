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

package org.apache.skywalking.oap.server.core.storage.query;

import com.google.gson.JsonObject;

import java.io.IOException;
import java.util.Base64;
import java.util.List;

import com.google.common.base.Charsets;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.apache.skywalking.oap.server.core.alarm.AlarmRecord;
import org.apache.skywalking.oap.server.core.alarm.AlarmSnapshotRecord;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.Tag;
import org.apache.skywalking.oap.server.core.query.enumeration.Scope;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.mqe.MQEMetric;
import org.apache.skywalking.oap.server.core.query.mqe.MQEValues;
import org.apache.skywalking.oap.server.core.query.type.AlarmMessage;
import org.apache.skywalking.oap.server.core.query.type.AlarmSnapshot;
import org.apache.skywalking.oap.server.core.query.type.Alarms;
import org.apache.skywalking.oap.server.core.query.type.KeyValue;
import org.apache.skywalking.oap.server.core.storage.DAO;
import org.apache.skywalking.oap.server.library.util.StringUtil;

public interface IAlarmQueryDAO extends DAO {

    Gson GSON = new Gson();

    Alarms getAlarm(final Integer scopeId, final String keyword, final int limit, final int from,
                    final Duration duration, final List<Tag> tags) throws IOException;

    /**
     * Parse the raw tags.
     */
    default void parseDataBinaryBase64(String dataBinaryBase64, List<KeyValue> tags) {
        parseDataBinary(Base64.getDecoder().decode(dataBinaryBase64), tags);
    }

    /**
     * Parse the raw tags.
     */
    default void parseDataBinary(byte[] dataBinary, List<KeyValue> tags) {
        List<Tag> tagList = GSON.fromJson(new String(dataBinary, Charsets.UTF_8), new TypeToken<List<Tag>>() {
        }.getType());
        tagList.forEach(pair -> tags.add(new KeyValue(pair.getKey(), pair.getValue())));
    }

    /**
     * Build the alarm message from the alarm record.
     * The Tags in JDBC storage is base64 encoded, need to decode in different way.
     */
    default AlarmMessage buildAlarmMessage(AlarmRecord alarmRecord, Long recoveryTime) {
        AlarmMessage message = new AlarmMessage();
        message.setId(String.valueOf(alarmRecord.getId0()));
        message.setId1(String.valueOf(alarmRecord.getId1()));
        message.setName(alarmRecord.getName());
        message.setMessage(alarmRecord.getAlarmMessage());
        message.setStartTime(alarmRecord.getStartTime());
        message.setRecoveryTime(recoveryTime);
        message.setScope(Scope.Finder.valueOf(alarmRecord.getScope()));
        message.setScopeId(alarmRecord.getScope());
        AlarmSnapshot alarmSnapshot = message.getSnapshot();
        message.setSnapshot(alarmSnapshot);
        String snapshot = alarmRecord.getSnapshot();
        if (StringUtil.isNotBlank(snapshot)) {
            AlarmSnapshotRecord alarmSnapshotRecord = GSON.fromJson(snapshot, AlarmSnapshotRecord.class);
            alarmSnapshot.setExpression(alarmSnapshotRecord.getExpression());
            JsonObject jsonObject = alarmSnapshotRecord.getMetrics();
            if (jsonObject != null) {
                for (final var obj : jsonObject.entrySet()) {
                    final var name = obj.getKey();
                    MQEMetric metrics = new MQEMetric();
                    metrics.setName(name);
                    List<MQEValues> values = GSON.fromJson(
                            obj.getValue().getAsString(), new TypeToken<List<MQEValues>>() {
                            }.getType());
                    metrics.setResults(values);
                    alarmSnapshot.getMetrics().add(metrics);
                }
            }
        }
        return message;
    }
}
