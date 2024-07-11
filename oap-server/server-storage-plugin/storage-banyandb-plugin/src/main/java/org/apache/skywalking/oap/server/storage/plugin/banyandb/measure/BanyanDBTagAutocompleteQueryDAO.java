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

package org.apache.skywalking.oap.server.storage.plugin.banyandb.measure;

import com.google.common.collect.ImmutableSet;
import org.apache.skywalking.banyandb.v1.client.DataPoint;
import org.apache.skywalking.banyandb.v1.client.MeasureQuery;
import org.apache.skywalking.banyandb.v1.client.MeasureQueryResponse;
import org.apache.skywalking.banyandb.v1.client.TimestampRange;
import org.apache.skywalking.oap.server.core.analysis.DownSampling;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.TagAutocompleteData;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.TagType;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.storage.query.ITagAutoCompleteQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBStorageClient;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.MetadataRegistry;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.stream.AbstractBanyanDBDAO;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static java.util.Objects.nonNull;

public class BanyanDBTagAutocompleteQueryDAO extends AbstractBanyanDBDAO implements ITagAutoCompleteQueryDAO {
    private static final Set<String> TAGS_KEY = ImmutableSet.of(TagAutocompleteData.TAG_TYPE,
            TagAutocompleteData.TAG_KEY);

    private static final Set<String> TAGS_KV = ImmutableSet.of(TagAutocompleteData.TAG_TYPE,
            TagAutocompleteData.TAG_KEY, TagAutocompleteData.TAG_VALUE);

    public BanyanDBTagAutocompleteQueryDAO(BanyanDBStorageClient client) {
        super(client);
    }

    @Override
    public Set<String> queryTagAutocompleteKeys(TagType tagType, int limit, Duration duration) throws IOException {
        MetadataRegistry.Schema schema = MetadataRegistry.INSTANCE.findMetadata(TagAutocompleteData.INDEX_NAME, DownSampling.Minute);
        long startMinTB = 0;
        long endMinTB = 0;
        if (nonNull(duration)) {
            startMinTB = duration.getStartTimeBucketInMin();
            endMinTB = duration.getEndTimeBucketInMin();
        }

        long startTB = TimeBucket.retainToDay4MinuteBucket(startMinTB);
        long endTB = TimeBucket.retainToDayLastMin4MinuteBucket(endMinTB);

        TimestampRange range = null;
        if (startTB > 0 && endTB > 0) {
            range = new TimestampRange(TimeBucket.getTimestamp(startTB), TimeBucket.getTimestamp(endTB));
        }
        MeasureQueryResponse resp = query(schema,
                TAGS_KEY, Collections.emptySet(),
                range,
                new QueryBuilder<MeasureQuery>() {
                    @Override
                    protected void apply(MeasureQuery query) {
                        query.groupBy(ImmutableSet.of(TagAutocompleteData.TAG_KEY));
                        query.setLimit(limit);
                        query.and(eq(TagAutocompleteData.TAG_TYPE, tagType.name()));
                    }
                }
        );

        if (resp.size() == 0) {
            return Collections.emptySet();
        }

        Set<String> keys = new HashSet<>();
        for (final DataPoint dp : resp.getDataPoints()) {
            keys.add(dp.getTagValue(TagAutocompleteData.TAG_KEY));
        }
        return keys;
    }

    @Override
    public Set<String> queryTagAutocompleteValues(TagType tagType, String tagKey, int limit, Duration duration) throws IOException {
        MetadataRegistry.Schema schema = MetadataRegistry.INSTANCE.findMetadata(TagAutocompleteData.INDEX_NAME, DownSampling.Minute);
        long startMinTB = 0;
        long endMinTB = 0;
        if (nonNull(duration)) {
            startMinTB = duration.getStartTimeBucketInMin();
            endMinTB = duration.getEndTimeBucketInMin();
        }

        long startTB = TimeBucket.retainToDay4MinuteBucket(startMinTB);
        long endTB = TimeBucket.retainToDayLastMin4MinuteBucket(endMinTB);

        TimestampRange range = null;
        if (startTB > 0 && endTB > 0) {
            range = new TimestampRange(TimeBucket.getTimestamp(startTB), TimeBucket.getTimestamp(endTB));
        }
        MeasureQueryResponse resp = query(schema,
                TAGS_KV, Collections.emptySet(),
                range,
                new QueryBuilder<MeasureQuery>() {
                    @Override
                    protected void apply(MeasureQuery query) {
                        query.groupBy(ImmutableSet.of(TagAutocompleteData.TAG_VALUE));
                        query.setLimit(limit);
                        query.and(eq(TagAutocompleteData.TAG_TYPE, tagType.name()));
                        query.and(eq(TagAutocompleteData.TAG_KEY, tagKey));
                    }
                }
        );

        if (resp.size() == 0) {
            return Collections.emptySet();
        }

        Set<String> values = new HashSet<>();
        for (final DataPoint dp : resp.getDataPoints()) {
            values.add(dp.getTagValue(TagAutocompleteData.TAG_VALUE));
        }
        return values;
    }
}
