/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.skywalking.oap.server.storage.plugin.jdbc.h2.dao;

import java.util.List;
import java.util.Map;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.Tag;
import org.apache.skywalking.oap.server.core.analysis.record.Record;
import org.apache.skywalking.oap.server.core.storage.StorageHashMapBuilder;

public abstract class AbstractSearchTagBuilder<T extends Record> implements StorageHashMapBuilder<T> {

    private final int numOfSearchableValuesPerTag;
    private final List<String> searchTagKeys;
    private final String tagColumn;

    public AbstractSearchTagBuilder(final int maxSizeOfArrayColumn,
                                    final int numOfSearchableValuesPerTag,
                                    final List<String> searchTagKeys,
                                    final String tagColumn) {
        this.numOfSearchableValuesPerTag = numOfSearchableValuesPerTag;
        final int maxNumOfTags = maxSizeOfArrayColumn / numOfSearchableValuesPerTag;
        if (searchTagKeys.size() > maxNumOfTags) {
            this.searchTagKeys = searchTagKeys.subList(0, maxNumOfTags);
        } else {
            this.searchTagKeys = searchTagKeys;
        }
        this.tagColumn = tagColumn;
    }

    protected void analysisSearchTag(List<Tag> rawTags, Map<String, Object> dbMap) {
        rawTags.forEach(tag -> {
            final int index = searchTagKeys.indexOf(tag.getKey());
            boolean shouldAdd = true;
            int tagInx = 0;
            final String tagExpression = tag.toString();
            for (int i = 0; i < numOfSearchableValuesPerTag; i++) {
                tagInx = index * numOfSearchableValuesPerTag + i;
                final String previousValue = (String) dbMap.get(tagColumn + "_" + tagInx);
                if (previousValue == null) {
                    // Still have at least one available slot, add directly.
                    shouldAdd = true;
                    break;
                }
                // If value is duplicated with added one, ignore.
                if (previousValue.equals(tagExpression)) {
                    shouldAdd = false;
                    break;
                }
                // Reach the end of tag
                if (i == numOfSearchableValuesPerTag - 1) {
                    shouldAdd = false;
                }
            }
            if (shouldAdd) {
                dbMap.put(tagColumn + "_" + tagInx, tagExpression);
            }
        });
    }
}
