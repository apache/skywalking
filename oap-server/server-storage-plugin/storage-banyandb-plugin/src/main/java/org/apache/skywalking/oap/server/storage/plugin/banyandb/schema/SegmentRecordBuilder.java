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

package org.apache.skywalking.oap.server.storage.plugin.banyandb.schema;

import com.google.common.collect.ImmutableList;
import org.apache.skywalking.banyandb.model.v1.BanyandbModel;
import org.apache.skywalking.banyandb.v1.client.SerializableTag;
import org.apache.skywalking.banyandb.v1.client.TagAndValue;
import org.apache.skywalking.oap.server.core.analysis.manual.segment.SegmentRecord;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SegmentRecordBuilder extends BanyanDBStorageDataBuilder<SegmentRecord> {
    public static final List<String> INDEXED_TAGS = ImmutableList.of(
            "http.method",
            "status_code",
            "db.type",
            "db.instance",
            "mq.queue",
            "mq.topic",
            "mq.broker"
    );

    @Override
    protected List<SerializableTag<BanyandbModel.TagValue>> searchableTags(SegmentRecord segmentRecord) {
        List<SerializableTag<BanyandbModel.TagValue>> searchable = new ArrayList<>(10);
        // 0 - trace_id
        searchable.add(TagAndValue.stringField(segmentRecord.getTraceId()));
        // 1 - is_error
        searchable.add(TagAndValue.longField(segmentRecord.getIsError()));
        // 2 - service_id
        searchable.add(TagAndValue.stringField(segmentRecord.getServiceId()));
        // 3 - service_instance_id
        searchable.add(TagAndValue.stringField(segmentRecord.getServiceInstanceId()));
        // 4 - endpoint_id
        searchable.add(TagAndValue.stringField(segmentRecord.getEndpointId()));
        // 5 - latency
        searchable.add(TagAndValue.longField(segmentRecord.getLatency()));
        // 6 - start_time
        searchable.add(TagAndValue.longField(segmentRecord.getStartTime()));
        // 7 ~ 13: indexed tags
        searchable.addAll(filterSearchableTags(segmentRecord.getTagsRawData(), INDEXED_TAGS));
        return searchable;
    }

    @Override
    protected List<SerializableTag<BanyandbModel.TagValue>> dataTags(SegmentRecord entity) {
        return Collections.singletonList(TagAndValue.binaryField(entity.getDataBinary()));
    }
}