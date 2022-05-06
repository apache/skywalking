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

package org.apache.skywalking.oap.server.storage.plugin.iotdb.query;

import com.google.common.base.Splitter;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.apache.iotdb.rpc.IoTDBConnectionException;
import org.apache.iotdb.rpc.StatementExecutionException;
import org.apache.iotdb.session.pool.SessionDataSetWrapper;
import org.apache.iotdb.session.pool.SessionPool;
import org.apache.iotdb.tsfile.read.common.RowRecord;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.TagAutocompleteData;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.TagType;
import org.apache.skywalking.oap.server.core.storage.StorageData;
import org.apache.skywalking.oap.server.core.storage.query.ITagAutoCompleteQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.iotdb.IoTDBClient;
import org.apache.skywalking.oap.server.storage.plugin.iotdb.IoTDBIndexes;
import org.apache.skywalking.oap.server.storage.plugin.iotdb.utils.IoTDBUtils;

@RequiredArgsConstructor
public class IoTDBTagAutoCompleteQueryDAO implements ITagAutoCompleteQueryDAO {
    private final IoTDBClient client;

    @Override
    public Set<String> queryTagAutocompleteKeys(final TagType tagType,
                                                final long startSecondTB,
                                                final long endSecondTB) throws IOException {
        StringBuilder query = new StringBuilder();
        query.append("select *").append(" from ");
        IoTDBUtils.addModelPath(client.getStorageGroup(), query, TagAutocompleteData.INDEX_NAME);
        Map<String, String> indexAndValueMap = new HashMap<>();
        indexAndValueMap.put(IoTDBIndexes.AUTOCOMPLETE_TAG_TYPE, tagType.name());
        IoTDBUtils.addQueryIndexValue(TagAutocompleteData.INDEX_NAME, query, indexAndValueMap);
        appendTagAutocompleteCondition(tagType, startSecondTB, endSecondTB, query);
        query.append(IoTDBClient.ALIGN_BY_DEVICE);

        SessionPool sessionPool = client.getSessionPool();
        SessionDataSetWrapper wrapper = null;
        Set<String> tagKeys = new HashSet<>();
        try {
            wrapper = sessionPool.executeQueryStatement(query.toString());
            while (wrapper.hasNext()) {
                RowRecord rowRecord = wrapper.next();
                List<String> resultList = Splitter.on(IoTDBClient.DOT + "\"")
                                                  .splitToList(rowRecord.getFields().get(0).getStringValue());
                String tagKey = resultList.get(resultList.size() - 2);
                tagKeys.add(tagKey.substring(0, tagKey.length() - 1));
            }
        } catch (IoTDBConnectionException | StatementExecutionException e) {
            throw new IOException(e);
        } finally {
            if (wrapper != null) {
                sessionPool.closeResultSet(wrapper);
            }
        }

        return tagKeys;
    }

    @Override
    public Set<String> queryTagAutocompleteValues(final TagType tagType,
                                                  final String tagKey,
                                                  final int limit,
                                                  final long startSecondTB,
                                                  final long endSecondTB) throws IOException {
        StringBuilder query = new StringBuilder();
        query.append("select * from ");
        IoTDBUtils.addModelPath(client.getStorageGroup(), query, TagAutocompleteData.INDEX_NAME);
        Map<String, String> indexAndValueMap = new HashMap<>();
        indexAndValueMap.put(IoTDBIndexes.AUTOCOMPLETE_TAG_KEY, tagKey);
        indexAndValueMap.put(IoTDBIndexes.AUTOCOMPLETE_TAG_TYPE, tagType.name());
        IoTDBUtils.addQueryIndexValue(TagAutocompleteData.INDEX_NAME, query, indexAndValueMap);
        appendTagAutocompleteCondition(tagType, startSecondTB, endSecondTB, query);
        query.append(" limit ").append(limit).append(IoTDBClient.ALIGN_BY_DEVICE);
        List<? super StorageData> storageDataList = client.filterQuery(TagAutocompleteData.INDEX_NAME,
                                                                       query.toString(),
                                                                       new TagAutocompleteData.Builder()
        );

        Set<String> tagValues = new HashSet<>();
        storageDataList.forEach(storageData -> {
            TagAutocompleteData tagAutocompleteData = (TagAutocompleteData) storageData;
            tagValues.add(tagAutocompleteData.getTagValue());
        });

        return tagValues;
    }

    private void appendTagAutocompleteCondition(final TagType tagType,
                                                final long startSecondTB,
                                                final long endSecondTB,
                                                final StringBuilder query) {
        long startMinTB = startSecondTB / 100;
        long endMinTB = endSecondTB / 100;

        StringBuilder where = new StringBuilder();
        if (startMinTB > 0) {
            where.append(IoTDBClient.TIME).append(" >= ").append(TimeBucket.getTimestamp(startMinTB));
        }
        if (endMinTB > 0) {
            if (where.length() > 0) {
                where.append(" and ");
            }
            where.append(IoTDBClient.TIME).append(" <= ").append(TimeBucket.getTimestamp(endMinTB));
        }
        if (where.length() > 0) {
            query.append(" where ").append(where);
        }
    }
}
