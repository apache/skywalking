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

package org.apache.skywalking.oap.server.core.alarm;

import java.util.List;
import org.apache.skywalking.oap.server.core.analysis.worker.RecordProcess;
import org.apache.skywalking.oap.server.library.util.TimeBucketUtils;
import org.slf4j.*;

/**
 * Save the alarm info into storage for UI query.
 *
 * @author wusheng, peng-yongsheng
 */
public class AlarmStandardPersistence implements AlarmCallback {

    private static final Logger logger = LoggerFactory.getLogger(AlarmStandardPersistence.class);

    @Override public void doAlarm(List<AlarmMessage> alarmMessage) {
        alarmMessage.forEach(message -> {
            if (logger.isDebugEnabled()) {
                logger.debug("Alarm message: {}", message.getAlarmMessage());
            }

            AlarmRecord record = new AlarmRecord();
            record.setScope(message.getScope().ordinal());
            record.setId0(message.getId0());
            record.setId1(message.getId1());
            record.setName(message.getName());
            record.setAlarmMessage(message.getAlarmMessage());
            record.setStartTime(message.getStartTime());
            record.setTimeBucket(TimeBucketUtils.INSTANCE.getSecondTimeBucket(message.getStartTime()));

            RecordProcess.INSTANCE.in(record);
        });
    }
}
