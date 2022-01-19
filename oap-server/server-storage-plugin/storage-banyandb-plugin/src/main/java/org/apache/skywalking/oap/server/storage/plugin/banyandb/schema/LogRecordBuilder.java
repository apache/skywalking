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
import org.apache.skywalking.oap.server.core.analysis.manual.log.LogRecord;

import java.util.ArrayList;
import java.util.List;

public class LogRecordBuilder extends BanyanDBStorageDataBuilder<LogRecord> {
    public static final List<String> INDEXED_TAGS = ImmutableList.of(
            "level"
    );

    @Override
    protected List<SerializableTag<BanyandbModel.TagValue>> searchableTags(LogRecord entity) {
        List<SerializableTag<BanyandbModel.TagValue>> searchable = new ArrayList<>();
        searchable.add(TagAndValue.stringField(entity.getUniqueId()));
        searchable.add(TagAndValue.stringField(entity.getServiceId()));
        searchable.add(TagAndValue.stringField(entity.getServiceInstanceId()));
        searchable.add(TagAndValue.stringField(entity.getServiceId()));
        searchable.add(TagAndValue.stringField(entity.getEndpointId()));
        searchable.add(TagAndValue.stringField(entity.getTraceId()));
        searchable.add(TagAndValue.stringField(entity.getTraceSegmentId()));
        searchable.add(TagAndValue.longField(entity.getSpanId()));
        searchable.addAll(filterSearchableTags(entity.getTags(), INDEXED_TAGS));
        return searchable;
    }

    @Override
    protected List<SerializableTag<BanyandbModel.TagValue>> dataTags(LogRecord entity) {
        return ImmutableList.of(
                TagAndValue.stringField(entity.getContent()),
                TagAndValue.longField(entity.getContentType()),
                TagAndValue.binaryField(entity.getTagsRawData())
        );
    }
}
