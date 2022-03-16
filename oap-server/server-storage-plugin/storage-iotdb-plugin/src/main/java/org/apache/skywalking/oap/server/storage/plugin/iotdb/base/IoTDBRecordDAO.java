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

package org.apache.skywalking.oap.server.storage.plugin.iotdb.base;

import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.apache.iotdb.tsfile.file.metadata.enums.TSDataType;
import org.apache.skywalking.oap.server.core.alarm.AlarmRecord;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.manual.log.LogRecord;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.Tag;
import org.apache.skywalking.oap.server.core.analysis.manual.segment.SegmentRecord;
import org.apache.skywalking.oap.server.core.analysis.record.Record;
import org.apache.skywalking.oap.server.core.storage.IRecordDAO;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.core.storage.type.StorageBuilder;
import org.apache.skywalking.oap.server.library.client.request.InsertRequest;

@RequiredArgsConstructor
public class IoTDBRecordDAO implements IRecordDAO {
    private final StorageBuilder<Record> storageBuilder;

    @Override
    public InsertRequest prepareBatchInsert(Model model, Record record) {
        final long timestamp = TimeBucket.getTimestamp(record.getTimeBucket(), model.getDownsampling());
        IoTDBInsertRequest request = new IoTDBInsertRequest(model.getName(), timestamp, record, storageBuilder);

        // transform tags of SegmentRecord, LogRecord, AlarmRecord to tag1, tag2, ...
        List<String> measurements = request.getMeasurements();
        List<TSDataType> measurementTypes = request.getMeasurementTypes();
        List<Object> measurementValues = request.getMeasurementValues();
        List<Tag> rawTags = null;
        if (SegmentRecord.INDEX_NAME.equals(model.getName())) {
            rawTags = ((SegmentRecord) record).getTagsRawData();
            measurementTypes.remove(measurements.indexOf(SegmentRecord.TAGS));
            measurementValues.remove(measurements.indexOf(SegmentRecord.TAGS));
            measurements.remove(SegmentRecord.TAGS);
        } else if (LogRecord.INDEX_NAME.equals(model.getName())) {
            rawTags = ((LogRecord) record).getTags();
            measurementTypes.remove(measurements.indexOf(LogRecord.TAGS));
            measurementValues.remove(measurements.indexOf(LogRecord.TAGS));
            measurements.remove(LogRecord.TAGS);
        } else if (AlarmRecord.INDEX_NAME.equals(model.getName())) {
            rawTags = ((AlarmRecord) record).getTags();
            measurementTypes.remove(measurements.indexOf(AlarmRecord.TAGS));
            measurementValues.remove(measurements.indexOf(AlarmRecord.TAGS));
            measurements.remove(AlarmRecord.TAGS);
        }
        if (Objects.nonNull(rawTags)) {
            rawTags.forEach(rawTag -> {
                if (rawTag.getKey().contains(".")) {
                    measurements.add("\"" + rawTag.getKey() + "\"");
                } else {
                    measurements.add(rawTag.getKey());
                }
                measurementTypes.add(TSDataType.TEXT);
                measurementValues.add(rawTag.getValue());
            });
        }
        return request;
    }
}
