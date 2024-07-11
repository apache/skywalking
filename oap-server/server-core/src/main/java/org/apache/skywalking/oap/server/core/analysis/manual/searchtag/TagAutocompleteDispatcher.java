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

package org.apache.skywalking.oap.server.core.analysis.manual.searchtag;

import org.apache.skywalking.oap.server.core.analysis.SourceDispatcher;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.worker.MetricsStreamProcessor;
import org.apache.skywalking.oap.server.core.source.TagAutocomplete;

public class TagAutocompleteDispatcher implements SourceDispatcher<TagAutocomplete> {

    @Override
    public void dispatch(TagAutocomplete source) {
        TagAutocompleteData autocomplete = new TagAutocompleteData();
        autocomplete.setTagKey(source.getTagKey());
        autocomplete.setTagValue(source.getTagValue());
        autocomplete.setTagType(source.getTagType().name());
        // change the precision in Day for reduce the storage
        autocomplete.setTimeBucket(TimeBucket.retainToDay4MinuteBucket(source.getTimeBucket()));
        MetricsStreamProcessor.getInstance().in(autocomplete);
    }
}
