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

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.server.core.analysis.MetricsExtension;
import org.apache.skywalking.oap.server.core.analysis.Stream;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.analysis.worker.MetricsStreamProcessor;
import org.apache.skywalking.oap.server.core.remote.grpc.proto.RemoteData;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.core.storage.StorageID;
import org.apache.skywalking.oap.server.core.storage.annotation.BanyanDB;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;
import org.apache.skywalking.oap.server.core.storage.type.Convert2Entity;
import org.apache.skywalking.oap.server.core.storage.type.Convert2Storage;
import org.apache.skywalking.oap.server.core.storage.type.StorageBuilder;

@Stream(name = TagAutocompleteData.INDEX_NAME, scopeId = DefaultScopeDefine.TAG_AUTOCOMPLETE,
    builder = TagAutocompleteData.Builder.class, processor = MetricsStreamProcessor.class)
// timeRelativeID=false at here doesn't mean the ID is completely irrelevant with time bucket.
// TagAutocompleteData still uses the day(toTimeBucketInDay()) as ID prefix,
// to make this tag tip feature doesn't host too large scale data.
@MetricsExtension(supportDownSampling = false, supportUpdate = false, timeRelativeID = false)
@EqualsAndHashCode(of = {
    "tagKey",
    "tagValue",
    "tagType"
})
@BanyanDB.IndexMode
public class TagAutocompleteData extends Metrics {
    public static final String INDEX_NAME = "tag_autocomplete";
    public static final String TAG_KEY = "tag_key";
    public static final String TAG_VALUE = "tag_value";
    public static final String TAG_TYPE = "tag_type";

    @Setter
    @Getter
    @Column(name = TAG_KEY)
    @BanyanDB.SeriesID(index = 1)
    private String tagKey;
    @Setter
    @Getter
    @Column(name = TAG_VALUE, length = Tag.TAG_LENGTH)
    @BanyanDB.SeriesID(index = 2)
    private String tagValue;

    @Setter
    @Getter
    @Column(name = TAG_TYPE)
    @BanyanDB.SeriesID(index = 0)
    private String tagType;

    @Override
    public boolean combine(final Metrics metrics) {
        return true;
    }

    @Override
    public void calculate() {

    }

    @Override
    public Metrics toHour() {
        return null;
    }

    @Override
    public Metrics toDay() {
        return null;
    }

    @Override
    protected StorageID id0() {
        return new StorageID()
            .appendMutant(new String[] {TIME_BUCKET}, toTimeBucketInDay())
            .append(TAG_TYPE, tagType)
            .append(TAG_KEY, tagKey)
            .append(TAG_VALUE, tagValue);
    }

    @Override
    public int remoteHashCode() {
        return this.hashCode();
    }

    @Override
    public void deserialize(final RemoteData remoteData) {
        setTagKey(remoteData.getDataStrings(0));
        setTagValue(remoteData.getDataStrings(1));
        setTagType(remoteData.getDataStrings(2));
        setTimeBucket(remoteData.getDataLongs(0));
    }

    @Override
    public RemoteData.Builder serialize() {
        final RemoteData.Builder builder = RemoteData.newBuilder();
        builder.addDataStrings(tagKey);
        builder.addDataStrings(tagValue);
        builder.addDataStrings(tagType);
        builder.addDataLongs(getTimeBucket());
        return builder;
    }

    public static class Builder implements StorageBuilder<TagAutocompleteData> {
        @Override
        public TagAutocompleteData storage2Entity(final Convert2Entity converter) {
            TagAutocompleteData record = new TagAutocompleteData();
            record.setTagKey((String) converter.get(TAG_KEY));
            record.setTagValue((String) converter.get(TAG_VALUE));
            record.setTagType((String) converter.get(TAG_TYPE));
            record.setTimeBucket(((Number) converter.get(TIME_BUCKET)).longValue());
            return record;
        }

        @Override
        public void entity2Storage(final TagAutocompleteData storageData, final Convert2Storage converter) {
            converter.accept(TAG_KEY, storageData.getTagKey());
            converter.accept(TAG_VALUE, storageData.getTagValue());
            converter.accept(TAG_TYPE, storageData.getTagType());
            converter.accept(TIME_BUCKET, storageData.getTimeBucket());
        }
    }
}
