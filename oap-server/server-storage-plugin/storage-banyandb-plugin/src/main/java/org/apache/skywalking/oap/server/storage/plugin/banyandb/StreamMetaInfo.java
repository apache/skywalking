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

package org.apache.skywalking.oap.server.storage.plugin.banyandb;

import com.google.common.base.Charsets;
import com.google.common.io.CharStreams;
import com.google.protobuf.util.JsonFormat;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.banyandb.database.v1.metadata.BanyandbMetadata;
import org.apache.skywalking.banyandb.v1.client.metadata.Duration;
import org.apache.skywalking.banyandb.v1.client.metadata.IndexRule;
import org.apache.skywalking.banyandb.v1.client.metadata.Stream;
import org.apache.skywalking.banyandb.v1.client.metadata.TagFamilySpec;
import org.apache.skywalking.oap.server.core.storage.model.Model;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
@Builder
@Slf4j
public class StreamMetaInfo {
    public static final String TAG_FAMILY_SEARCHABLE = "searchable";
    public static final String TAG_FAMILY_DATA = "data";

    private static final Map<String, StreamMetaInfo> STREAMS = new HashMap<>();

    private final Model model;

    /**
     * stream is the metadata to be used for schema creation,
     * 1. Read json from resources/metadata/{model.name}.json and deserialize to protobuf,
     * 2. Iterate over tag families,
     * 3. Iterate over tags in each tag family
     * 4.
     */
    private final Stream stream;

    private final List<IndexRule> indexRules;

    public static StreamMetaInfo addModel(Model model) {
        BanyandbMetadata.Stream pbStream = parseStreamFromJSON(model.getName());
        if (pbStream == null) {
            log.warn("fail to find the stream schema {}", model.getName());
            return null;
        }
        BanyandbMetadata.Duration duration = pbStream.getOpts().getTtl();
        Duration ttl = fromProtobuf(duration);
        final Stream stream = new Stream(pbStream.getMetadata().getName(), pbStream.getOpts().getShardNum(), ttl);

        List<IndexRule> indexRules = new ArrayList<>();

        stream.setEntityTagNames(pbStream.getEntity().getTagNamesList());
        for (BanyandbMetadata.TagFamilySpec pbTagFamilySpec : pbStream.getTagFamiliesList()) {
            final TagFamilySpec tagFamilySpec = new TagFamilySpec(pbTagFamilySpec.getName());
            final boolean needIndexParse = pbTagFamilySpec.getName().equals(TAG_FAMILY_SEARCHABLE);
            for (final BanyandbMetadata.TagSpec pbTagSpec : pbTagFamilySpec.getTagsList()) {
                tagFamilySpec.addTagSpec(parseTagSpec(pbTagSpec));

                // if the tag family equals to "searchable", build index rules
                if (needIndexParse) {
                    BanyandbMetadata.IndexRule pbIndexRule = parseIndexRulesFromJSON(model.getName(), pbTagSpec.getName());
                    if (pbIndexRule == null) {
                        log.warn("fail to find the index rule for {}", pbTagSpec.getName());
                        continue;
                    }
                    IndexRule.IndexType indexType = fromProtobuf(pbIndexRule.getType());
                    IndexRule.IndexLocation indexLocation = fromProtobuf(pbIndexRule.getLocation());
                    IndexRule indexRule = new IndexRule(pbIndexRule.getMetadata().getName(), indexType, indexLocation);
                    indexRule.setTags(new ArrayList<>(pbIndexRule.getTagsList()));
                    indexRules.add(indexRule);
                }
            }
        }

        return StreamMetaInfo.builder().model(model).stream(stream).indexRules(indexRules).build();
    }

    private static TagFamilySpec.TagSpec parseTagSpec(BanyandbMetadata.TagSpec pbTagSpec) {
        switch (pbTagSpec.getType()) {
            case TAG_TYPE_INT:
                return TagFamilySpec.TagSpec.newIntTag(pbTagSpec.getName());
            case TAG_TYPE_INT_ARRAY:
                return TagFamilySpec.TagSpec.newIntArrayTag(pbTagSpec.getName());
            case TAG_TYPE_STRING:
                return TagFamilySpec.TagSpec.newStringTag(pbTagSpec.getName());
            case TAG_TYPE_STRING_ARRAY:
                return TagFamilySpec.TagSpec.newStringArrayTag(pbTagSpec.getName());
            case TAG_TYPE_DATA_BINARY:
                return TagFamilySpec.TagSpec.newBinaryTag(pbTagSpec.getName());
            default:
                throw new IllegalArgumentException("unrecognized tag type");
        }
    }

    private static BanyandbMetadata.Stream parseStreamFromJSON(String name) {
        try {
            InputStream is = StreamMetaInfo.class.getClassLoader().getResourceAsStream("metadata/" + name + ".json");
            if (is == null) {
                return null;
            }
            String result = CharStreams.toString(new InputStreamReader(is, Charsets.UTF_8));
            BanyandbMetadata.Stream.Builder b = BanyandbMetadata.Stream.newBuilder();
            JsonFormat.parser().merge(result, b);
            return b.build();
        } catch (IOException ioEx) {
            log.error("fail to read json", ioEx);
            return null;
        }
    }

    private static BanyandbMetadata.IndexRule parseIndexRulesFromJSON(String streamName, String name) {
        try {
            InputStream is = StreamMetaInfo.class.getClassLoader().getResourceAsStream(String.join("/",
                    new String[]{"metadata", "index_rules", streamName, name + ".json"}));
            if (is == null) {
                return null;
            }
            String result = CharStreams.toString(new InputStreamReader(is, Charsets.UTF_8));
            BanyandbMetadata.IndexRule.Builder b = BanyandbMetadata.IndexRule.newBuilder();
            JsonFormat.parser().merge(result, b);
            return b.build();
        } catch (IOException ioEx) {
            log.error("fail to read json", ioEx);
            return null;
        }
    }

    // TODO: change modifier to public in SDK
    static Duration fromProtobuf(BanyandbMetadata.Duration duration) {
        switch (duration.getUnit()) {
            case DURATION_UNIT_DAY:
                return Duration.ofDays(duration.getVal());
            case DURATION_UNIT_HOUR:
                return Duration.ofHours(duration.getVal());
            case DURATION_UNIT_MONTH:
                return Duration.ofMonths(duration.getVal());
            case DURATION_UNIT_WEEK:
                return Duration.ofWeeks(duration.getVal());
            default:
                throw new IllegalArgumentException("unrecognized DurationUnit");
        }
    }

    // TODO: change modifier to public in SDK
    private static IndexRule.IndexType fromProtobuf(BanyandbMetadata.IndexRule.Type type) {
        switch (type) {
            case TYPE_TREE:
                return IndexRule.IndexType.TREE;
            case TYPE_INVERTED:
                return IndexRule.IndexType.INVERTED;
            default:
                throw new IllegalArgumentException("unrecognized index type");
        }
    }

    // TODO: change modifier to public in SDK
    private static IndexRule.IndexLocation fromProtobuf(BanyandbMetadata.IndexRule.Location loc) {
        switch (loc) {
            case LOCATION_GLOBAL:
                return IndexRule.IndexLocation.GLOBAL;
            case LOCATION_SERIES:
                return IndexRule.IndexLocation.SERIES;
            default:
                throw new IllegalArgumentException("unrecognized index location");
        }
    }
}
