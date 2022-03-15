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

package org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao;

import java.util.List;
import org.apache.skywalking.oap.server.core.analysis.manual.segment.SegmentRecord;
import org.apache.skywalking.oap.server.core.analysis.record.Record;
import org.apache.skywalking.oap.server.core.storage.type.Convert2Entity;
import org.apache.skywalking.oap.server.core.storage.type.Convert2Storage;
import org.apache.skywalking.oap.server.storage.plugin.jdbc.HashMapConverterWrapper;

import static org.apache.skywalking.oap.server.core.analysis.manual.segment.SegmentRecord.TAGS;

/**
 * H2/MySQL is different from standard {@link org.apache.skywalking.oap.server.core.analysis.manual.segment.SegmentRecord.Builder},
 * this maps the tags into multiple columns.
 */
public class H2SegmentRecordBuilder extends AbstractSearchTagBuilder<Record> {

    public H2SegmentRecordBuilder(final int maxSizeOfArrayColumn,
                                  final int numOfSearchableValuesPerTag,
                                  final List<String> searchTagKeys) {
        super(maxSizeOfArrayColumn, numOfSearchableValuesPerTag, searchTagKeys, TAGS);
    }

    @Override
    public Record storage2Entity(final Convert2Entity converter) {
        return new SegmentRecord.Builder().storage2Entity(converter);
    }

    @Override
    public void entity2Storage(final Record record, final Convert2Storage originConverter) {
        SegmentRecord storageData = (SegmentRecord) record;
        Convert2Storage converter = HashMapConverterWrapper.of(originConverter);
        final SegmentRecord.Builder builder = new SegmentRecord.Builder();
        builder.entity2Storage(storageData, converter);
        analysisSearchTag(storageData.getTagsRawData(), converter);
    }
}