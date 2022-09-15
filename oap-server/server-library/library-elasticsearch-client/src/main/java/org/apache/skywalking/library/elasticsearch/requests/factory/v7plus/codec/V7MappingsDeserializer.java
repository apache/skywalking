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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import org.apache.skywalking.library.elasticsearch.response.Mappings;

final class V7MappingsDeserializer extends JsonDeserializer<Mappings> {
    @Override
    @SuppressWarnings("unchecked")
    public Mappings deserialize(final JsonParser p, final DeserializationContext ctxt)
        throws IOException {

        final Map<String, Object> m =
            p.getCodec().readValue(p, new TypeReference<Map<String, Object>>() {
            });

        if (m.size() > 0) {
            Map<String, Object> properties = (Map<String, Object>) m.get("properties");
            Map<String, Object> source = (Map<String, Object>) m.get("_source");
            final Mappings mappings = new Mappings();
            mappings.setType("_doc");
            if (properties != null) {
                mappings.setProperties(properties);
            }
            if (source != null) {
                Object excludes = source.get("excludes");
                if (excludes != null) {
                    mappings.getSource().setExcludes(new HashSet<>((ArrayList<String>) excludes));
                }
            }
            return mappings;
        }
        return null;
    }
}
