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

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import org.apache.skywalking.library.elasticsearch.response.IndexTemplate;
import org.apache.skywalking.library.elasticsearch.response.IndexTemplates;

final class V7IndexTemplatesDeserializer extends JsonDeserializer<IndexTemplates> {
    public static final TypeReference<Map<String, IndexTemplate>> TYPE_REFERENCE =
        new TypeReference<Map<String, IndexTemplate>>() {
        };

    @Override
    public IndexTemplates deserialize(final JsonParser p,
                                      final DeserializationContext ctxt)
        throws IOException {

        final Map<String, IndexTemplate> templates = p.getCodec().readValue(p, TYPE_REFERENCE);
        if (templates == null) {
            return new IndexTemplates(Collections.emptyMap());
        }
        return new IndexTemplates(templates);
    }
}
