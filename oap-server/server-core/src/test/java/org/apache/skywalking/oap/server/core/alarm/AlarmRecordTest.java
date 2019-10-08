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

import org.apache.skywalking.oap.server.core.Const;
import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * @author jsbxyyx
 */
public class AlarmRecordTest {

    @Test
    public void idVerify() throws Exception {
        AlarmRecord alarmRecord = new AlarmRecord();
        String id = alarmRecord.id();
        String id1 = alarmRecord.getTimeBucket() + Const.ID_SPLIT
                + alarmRecord.getScope() + Const.ID_SPLIT
                + alarmRecord.getId0() + Const.ID_SPLIT
                + alarmRecord.getId1();
        Assert.assertEquals(id1, id);
    }

    @Test
    public void builderData2MapVerify() throws Exception {
        AlarmRecord.Builder builder = new AlarmRecord.Builder();
        Map<String, Object> map = builder.data2Map(new AlarmRecord());
        Assert.assertEquals(true, map.containsKey(AlarmRecord.SCOPE));
        Assert.assertEquals(true, map.containsKey(AlarmRecord.NAME));
        Assert.assertEquals(true, map.containsKey(AlarmRecord.ID0));
        Assert.assertEquals(true, map.containsKey(AlarmRecord.ID1));
        Assert.assertEquals(true, map.containsKey(AlarmRecord.ALARM_MESSAGE));
        Assert.assertEquals(true, map.containsKey(AlarmRecord.START_TIME));
        Assert.assertEquals(true, map.containsKey(AlarmRecord.TIME_BUCKET));
    }

    @Test
    public void builderNPEMap2DataVerify() throws Exception {
        AlarmRecord.Builder builder = new AlarmRecord.Builder();

        try {
            builder.map2Data(null);
        } catch (NullPointerException e) {
            Assert.assertFalse(false);
        }

        Map<String, Object> dbMap = new HashMap<>();
        try {
            builder.map2Data(dbMap);
        } catch (NullPointerException e) {
            Assert.assertFalse(false);
        }
    }

    @Test
    public void builderMap2DataVerify() throws Exception {
        AlarmRecord.Builder builder = new AlarmRecord.Builder();

        Map<String, Object> dbMap = new HashMap<>();
        dbMap.put(AlarmRecord.SCOPE, 1);
        dbMap.put(AlarmRecord.NAME, "");
        dbMap.put(AlarmRecord.ID0, 1);
        dbMap.put(AlarmRecord.ID1, 1);
        dbMap.put(AlarmRecord.ALARM_MESSAGE, "");
        dbMap.put(AlarmRecord.START_TIME, 1L);
        dbMap.put(AlarmRecord.TIME_BUCKET, 1L);
        AlarmRecord alarmRecord = builder.map2Data(dbMap);

        Assert.assertEquals(1L, alarmRecord.getTimeBucket());
        Assert.assertEquals(1L, alarmRecord.getStartTime());
        Assert.assertEquals("", alarmRecord.getAlarmMessage());
        Assert.assertEquals(1, alarmRecord.getId1());
        Assert.assertEquals(1, alarmRecord.getId0());
        Assert.assertEquals("", alarmRecord.getName());
        Assert.assertEquals(1, alarmRecord.getScope());
    }
}
