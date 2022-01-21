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
import org.apache.skywalking.banyandb.database.v1.BanyandbDatabase;
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

    public static final String ID = "id";

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
        BanyandbDatabase.Stream pbStream = parseStreamFromJSON(model.getName());
        if (pbStream == null) {
            log.warn("fail to find stream schema {}", model.getName());
            return null;
        }
        BanyandbDatabase.Duration duration = pbStream.getOpts().getTtl();
        Duration ttl = Duration.fromProtobuf(duration);
        final Stream stream = new Stream(pbStream.getMetadata().getName(), pbStream.getOpts().getShardNum(), ttl);

        List<IndexRule> indexRules = new ArrayList<>();

        stream.setEntityTagNames(pbStream.getEntity().getTagNamesList());


        for (BanyandbDatabase.TagFamilySpec pbTagFamilySpec : pbStream.getTagFamiliesList()) {
            final TagFamilySpec tagFamilySpec = TagFamilySpec.fromProtobuf(pbTagFamilySpec);
            stream.addTagFamilySpec(tagFamilySpec);

            // if the tag family equals to "searchable", build index rules
            if (tagFamilySpec.getTagFamilyName().equals(TAG_FAMILY_SEARCHABLE)) {
                for (final TagFamilySpec.TagSpec tagSpec : tagFamilySpec.getTagSpecs()) {
                    BanyandbDatabase.IndexRule pbIndexRule = parseIndexRulesFromJSON(model.getName(), tagSpec.getTagName());
                    if (pbIndexRule == null) {
                        log.warn("fail to find the index rule for {}", tagSpec.getTagName());
                        continue;
                    }
                    IndexRule indexRule = IndexRule.fromProtobuf(pbIndexRule);
                    indexRules.add(indexRule);
                }
            }
        }

        return StreamMetaInfo.builder().model(model).stream(stream).indexRules(indexRules).build();
    }

    private static BanyandbDatabase.Stream parseStreamFromJSON(String name) {
        try {
            InputStream is = StreamMetaInfo.class.getClassLoader().getResourceAsStream("metadata/" + name + ".json");
            if (is == null) {
                log.warn("fail to find definition for {}", name);
                return null;
            }
            String result = CharStreams.toString(new InputStreamReader(is, Charsets.UTF_8));
            BanyandbDatabase.Stream.Builder b = BanyandbDatabase.Stream.newBuilder();
            JsonFormat.parser().merge(result, b);
            return b.build();
        } catch (IOException ioEx) {
            log.error("fail to read json", ioEx);
            return null;
        }
    }

    private static BanyandbDatabase.IndexRule parseIndexRulesFromJSON(String streamName, String name) {
        try {
            InputStream is = StreamMetaInfo.class.getClassLoader().getResourceAsStream(String.join("/",
                    new String[]{"metadata", "index_rules", name + ".json"}));
            if (is == null) {
                log.warn("fail to find index rules for {}", streamName);
                return null;
            }
            String result = CharStreams.toString(new InputStreamReader(is, Charsets.UTF_8));
            BanyandbDatabase.IndexRule.Builder b = BanyandbDatabase.IndexRule.newBuilder();
            JsonFormat.parser().merge(result, b);
            return b.build();
        } catch (IOException ioEx) {
            log.error("fail to read json", ioEx);
            return null;
        }
    }
}
