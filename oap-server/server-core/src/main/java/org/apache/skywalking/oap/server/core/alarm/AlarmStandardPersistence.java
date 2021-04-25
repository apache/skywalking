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

import com.google.common.base.Charsets;
import com.google.gson.Gson;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.Tag;
import org.apache.skywalking.oap.server.core.analysis.worker.RecordStreamProcessor;
import org.apache.skywalking.oap.server.core.config.ConfigService;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

/**
 * Save the alarm info into storage for UI query.
 */
public class AlarmStandardPersistence implements AlarmCallback {

    private static final Logger LOGGER = LoggerFactory.getLogger(AlarmStandardPersistence.class);
    private final Gson gson = new Gson();
    private final ModuleManager manager;

    public AlarmStandardPersistence(ModuleManager manager) {
        this.manager = manager;
    }

    @Override
    public void doAlarm(List<AlarmMessage> alarmMessage) {
        alarmMessage.forEach(message -> {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Alarm message: {}", message.getAlarmMessage());
            }

            AlarmRecord record = new AlarmRecord();
            record.setScope(message.getScopeId());
            record.setId0(message.getId0());
            record.setId1(message.getId1());
            record.setName(message.getName());
            record.setAlarmMessage(message.getAlarmMessage());
            record.setStartTime(message.getStartTime());
            record.setTimeBucket(TimeBucket.getRecordTimeBucket(message.getStartTime()));
            record.setRuleName(message.getRuleName());
            Collection<Tag> tags = appendSearchableTags(message.getTags());
            record.setTags(new ArrayList<>(tags));
            record.setTagsRawData(gson.toJson(message.getTags()).getBytes(Charsets.UTF_8));
            record.setTagsInString(Tag.Util.toStringList(new ArrayList<>(tags)));
            RecordStreamProcessor.getInstance().in(record);
        });
    }

    private Collection<Tag> appendSearchableTags(List<Tag> tags) {
        final ConfigService configService = manager.find(CoreModule.NAME)
                .provider()
                .getService(ConfigService.class);
        HashSet<Tag> alarmTags = new HashSet<>();
        tags.forEach(tag -> {
            if (configService.getSearchableAlarmTags().contains(tag.getKey())) {
                final Tag alarmTag = new Tag(tag.getKey(), tag.getValue());
                alarmTags.add(alarmTag);
            }
        });
        return alarmTags;
    }
}
