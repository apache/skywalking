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
 */

package org.apache.skywalking.library.elasticsearch.requests.factory.v7plus.codec;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.io.SerializedString;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Strings;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.library.elasticsearch.response.IndexTemplate;
import org.apache.skywalking.library.elasticsearch.response.IndexTemplates;

import static java.util.stream.Collectors.toMap;

@Slf4j
final class V78IndexTemplatesDeserializer extends JsonDeserializer<IndexTemplates> {
    private static final TypeReference<List<IndexTemplateWrapper>> TYPE_REFERENCE =
        new TypeReference<List<IndexTemplateWrapper>>() {
        };

    @Override
    public IndexTemplates deserialize(final JsonParser p,
                                      final DeserializationContext ctxt)
        throws IOException {
        while (!p.nextFieldName(new SerializedString("index_templates"))) {
            if (p.currentName() == null) {
                return new IndexTemplates(Collections.emptyMap());
            }
            p.skipChildren();
        }
        if (p.nextToken() != JsonToken.START_ARRAY) {
            throw new UnsupportedOperationException(
                "this might be a new ElasticSearch version and we don't support yet");
        }

        final JsonNode array = p.getCodec().readTree(p);
        final List<IndexTemplate> templates = new ArrayList<>(array.size());
        for (final JsonNode node : array) {
            final String name = node.get("name").asText();
            if (Strings.isNullOrEmpty(name)) {
                log.error("index template without a name: {}", node);
                continue;
            }

            final JsonNode indexTemplateNode = node.get("index_template");
            if (indexTemplateNode == null) {
                log.error("index template without index_template: {}", node);
                continue;
            }
            final IndexTemplateWrapper wrapper =
                p.getCodec().treeToValue(indexTemplateNode, IndexTemplateWrapper.class);
            wrapper.getTemplate().setName(name);
            wrapper.getTemplate().setIndexPatterns(wrapper.getIndexPatterns());
            templates.add(wrapper.getTemplate());
        }

        final Map<String, IndexTemplate> templateMap =
            templates.stream()
                     .collect(toMap(IndexTemplate::getName, Function.identity()));
        return new IndexTemplates(templateMap);
    }

    @Data
    static final class IndexTemplateWrapper {
        @JsonProperty("index_patterns")
        private List<String> indexPatterns;
        private IndexTemplate template;
    }
}
