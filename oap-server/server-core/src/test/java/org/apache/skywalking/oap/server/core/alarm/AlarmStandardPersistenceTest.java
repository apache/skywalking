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
 */

package org.apache.skywalking.oap.server.core.alarm;

import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.record.Record;
import org.apache.skywalking.oap.server.core.analysis.worker.RecordPersistentWorker;
import org.apache.skywalking.oap.server.core.analysis.worker.RecordStreamProcessor;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * @author jsbxyyx
 */
public class AlarmStandardPersistenceTest {

    @Test
    public void doAlarmNPEVerify() throws Exception {
        AlarmStandardPersistence persistence = new AlarmStandardPersistence();
        try {
            persistence.doAlarm(null);
            Assert.fail("Should throws NPE");
        } catch (NullPointerException e) {
        }
    }

    @Test
    public void doAlarmVerify() throws Exception {

        RecordPersistentWorker recordPersistentWorker = mock(RecordPersistentWorker.class);

        Map<Class<? extends Record>, RecordPersistentWorker> workers = new HashMap<>();
        workers.put(AlarmRecord.class, recordPersistentWorker);
        Field field = RecordStreamProcessor.class.getDeclaredField("workers");
        field.setAccessible(true);
        field.set(RecordStreamProcessor.getInstance(), workers);

        List<AlarmMessage> alarmMessageList = new ArrayList<>();
        AlarmMessage alarmMessage = new AlarmMessage();
        alarmMessage.setScopeId(1);
        alarmMessage.setAlarmMessage("");
        alarmMessage.setId0(0);
        alarmMessage.setId1(1);
        alarmMessage.setName("name");
        alarmMessage.setStartTime(1L);
        alarmMessageList.add(alarmMessage);

        AlarmStandardPersistence persistence = new AlarmStandardPersistence();
        persistence.doAlarm(alarmMessageList);

        ArgumentCaptor<AlarmRecord> argumentCaptor = ArgumentCaptor.forClass(AlarmRecord.class);
        verify(recordPersistentWorker).in(argumentCaptor.capture());

        Assert.assertEquals(alarmMessage.getScopeId(), argumentCaptor.getValue().getScope());
        Assert.assertEquals(alarmMessage.getAlarmMessage(), argumentCaptor.getValue().getAlarmMessage());
        Assert.assertEquals(alarmMessage.getId0(), argumentCaptor.getValue().getId0());
        Assert.assertEquals(alarmMessage.getId1(), argumentCaptor.getValue().getId1());
        Assert.assertEquals(alarmMessage.getName(), argumentCaptor.getValue().getName());
        Assert.assertEquals(alarmMessage.getStartTime(), argumentCaptor.getValue().getStartTime());
        Assert.assertEquals(TimeBucket.getRecordTimeBucket(alarmMessage.getStartTime()), argumentCaptor.getValue().getTimeBucket());
    }

}
