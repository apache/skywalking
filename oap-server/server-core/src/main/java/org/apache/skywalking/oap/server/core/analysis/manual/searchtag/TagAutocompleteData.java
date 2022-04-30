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
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.remote.grpc.proto.RemoteData;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;

@EqualsAndHashCode(of = {
    "tag"
})
public abstract class TagAutocompleteData extends Metrics {
    public static final String TAG_KEY = "tag_key";
    public static final String TAG_VALUE = "tag_value";

    @Setter
    @Getter
    private String tag;
    @Setter
    @Getter
    @Column(columnName = TAG_KEY)
    private String tagKey;
    @Setter
    @Getter
    @Column(columnName = TAG_VALUE)
    private String tagValue;

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
    protected String id0() {
        return toTimeBucketInDay() + "-" + tag;
    }

    @Override
    public int remoteHashCode() {
        return this.hashCode();
    }

    @Override
    public void deserialize(final RemoteData remoteData) {
        setTag(remoteData.getDataStrings(0));
        setTagKey(remoteData.getDataStrings(1));
        setTagValue(remoteData.getDataStrings(2));
        setTimeBucket(remoteData.getDataLongs(0));
    }

    @Override
    public RemoteData.Builder serialize() {
        final RemoteData.Builder builder = RemoteData.newBuilder();
        builder.addDataStrings(tag);
        builder.addDataStrings(tagKey);
        builder.addDataStrings(tagValue);
        builder.addDataLongs(getTimeBucket());
        return builder;
    }
}
