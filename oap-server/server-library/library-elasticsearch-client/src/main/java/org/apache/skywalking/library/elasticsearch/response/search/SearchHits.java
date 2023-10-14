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

package org.apache.skywalking.library.elasticsearch.response.search;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public final class SearchHits implements Iterable<SearchHit> {
    @JsonDeserialize(using = TotalDeserializer.class)
    private int total;
    private List<SearchHit> hits;

    public List<SearchHit> getHits() {
        if (hits != null) {
            return hits;
        }
        return Collections.emptyList();
    }

    @Override
    public Iterator<SearchHit> iterator() {
        return getHits().iterator();
    }

    public static final class TotalDeserializer extends JsonDeserializer<Integer> {
        @Override
        public Integer deserialize(final JsonParser p, final DeserializationContext ctxt)
            throws IOException {
            final JsonNode node = p.getCodec().readTree(p);
            if (node.isInt()) {
                return node.asInt();
            }
            final JsonNode value = node.get("value");
            if (value.isInt()) {
                return value.asInt();
            }
            throw new UnsupportedOperationException("Search response total field is not integer");
        }
    }
}
