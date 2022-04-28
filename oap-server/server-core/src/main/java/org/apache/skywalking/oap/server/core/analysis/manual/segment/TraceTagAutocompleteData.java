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

package org.apache.skywalking.oap.server.core.analysis.manual.segment;

import lombok.EqualsAndHashCode;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.analysis.MetricsExtension;
import org.apache.skywalking.oap.server.core.analysis.Stream;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.TagAutocompleteData;
import org.apache.skywalking.oap.server.core.analysis.worker.MetricsStreamProcessor;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.core.storage.type.Convert2Entity;
import org.apache.skywalking.oap.server.core.storage.type.Convert2Storage;
import org.apache.skywalking.oap.server.core.storage.type.StorageBuilder;

@Stream(name = TraceTagAutocompleteData.INDEX_NAME, scopeId = DefaultScopeDefine.TRACE_TAG_AUTOCOMPLETE,
    builder = TraceTagAutocompleteData.Builder.class, processor = MetricsStreamProcessor.class)
@MetricsExtension(supportDownSampling = false, supportUpdate = false, timeRelativeID = true)
@EqualsAndHashCode(callSuper = true)
public class TraceTagAutocompleteData extends TagAutocompleteData {
    public static final String INDEX_NAME = "trace_tag_autocomplete";

    public static class Builder implements StorageBuilder<TraceTagAutocompleteData> {
        @Override
        public TraceTagAutocompleteData storage2Entity(final Convert2Entity converter) {
            TraceTagAutocompleteData record = new TraceTagAutocompleteData();
            record.setTagKey((String) converter.get(TAG_KEY));
            record.setTagValue((String) converter.get(TAG_VALUE));
            record.setTag(record.getTagKey() + "=" + record.getTagValue());
            return record;
        }

        @Override
        public void entity2Storage(final TraceTagAutocompleteData storageData, final Convert2Storage converter) {
            converter.accept(TAG_KEY, storageData.getTagKey());
            converter.accept(TAG_VALUE, storageData.getTagValue());
            converter.accept(TIME_BUCKET, storageData.getTimeBucket());
        }
    }
}
