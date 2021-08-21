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
import org.apache.iotdb.tsfile.file.metadata.enums.TSDataType;
import org.apache.skywalking.apm.commons.datacarrier.common.AtomicRangeInteger;
import org.apache.skywalking.oap.server.core.alarm.AlarmRecord;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.manual.log.LogRecord;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.Tag;
import org.apache.skywalking.oap.server.core.analysis.manual.segment.SegmentRecord;
import org.apache.skywalking.oap.server.core.analysis.record.Record;
import org.apache.skywalking.oap.server.core.storage.IRecordDAO;
import org.apache.skywalking.oap.server.core.storage.StorageHashMapBuilder;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.library.client.request.InsertRequest;

public class IoTDBRecordDAO implements IRecordDAO {
    private static final int PADDING_SIZE = 1_000_000;
    private static final AtomicRangeInteger SUFFIX = new AtomicRangeInteger(0, PADDING_SIZE);

    private final StorageHashMapBuilder<Record> storageBuilder;

    public IoTDBRecordDAO(StorageHashMapBuilder<Record> storageBuilder) {
        this.storageBuilder = storageBuilder;
    }

    @Override
    public InsertRequest prepareBatchInsert(Model model, Record record) {
        final long timestamp = TimeBucket.getTimestamp(record.getTimeBucket(), model.getDownsampling());
        IoTDBInsertRequest request = new IoTDBInsertRequest(model.getName(), timestamp, record, storageBuilder);

        // transform tags of SegmentRecord, LogRecord, AlarmRecord to tag1, tag2, ...
        List<String> timeseriesList = request.getTimeseriesList();
        List<TSDataType> timeseriesTypes = request.getTimeseriesTypes();
        List<Object> timeseriesValues = request.getTimeseriesValues();
        List<Tag> rawTags = null;
        if (SegmentRecord.INDEX_NAME.equals(model.getName())) {
            rawTags = ((SegmentRecord) record).getTagsRawData();
            timeseriesTypes.remove(timeseriesList.indexOf(SegmentRecord.TAGS));
            timeseriesValues.remove(timeseriesList.indexOf(SegmentRecord.TAGS));
            timeseriesList.remove(SegmentRecord.TAGS);
        } else if (LogRecord.INDEX_NAME.equals(model.getName())) {
            rawTags = ((LogRecord) record).getTags();
            timeseriesTypes.remove(timeseriesList.indexOf(LogRecord.TAGS));
            timeseriesValues.remove(timeseriesList.indexOf(LogRecord.TAGS));
            timeseriesList.remove(LogRecord.TAGS);
        } else if (AlarmRecord.INDEX_NAME.equals(model.getName())) {
            rawTags = ((AlarmRecord) record).getTags();
            timeseriesTypes.remove(timeseriesList.indexOf(AlarmRecord.TAGS));
            timeseriesValues.remove(timeseriesList.indexOf(AlarmRecord.TAGS));
            timeseriesList.remove(AlarmRecord.TAGS);
        }
        if (Objects.nonNull(rawTags)) {
            rawTags.forEach(rawTag -> {
                timeseriesList.add(rawTag.getKey());
                timeseriesTypes.add(TSDataType.TEXT);
                timeseriesValues.add(rawTag.getValue());
            });
        }
        request.setTimeseriesList(timeseriesList);
        request.setTimeseriesTypes(timeseriesTypes);
        request.setTimeseriesValues(timeseriesValues);
        return request;
    }
}
