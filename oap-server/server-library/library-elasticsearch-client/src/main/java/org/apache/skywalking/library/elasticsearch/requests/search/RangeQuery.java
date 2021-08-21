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

package org.apache.skywalking.library.elasticsearch.requests.search;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.io.IOException;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@JsonSerialize(using = RangeQuery.Serializer.class)
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public final class RangeQuery extends Query {
    private final String name;
    private final Object gte;
    private final Object gt;
    private final Object lte;
    private final Object lt;
    private final Double boost;

    static final class Serializer extends JsonSerializer<RangeQuery> {
        static final String NAME = "range";

        @Override
        public void serialize(final RangeQuery value, final JsonGenerator gen,
                              final SerializerProvider provider) throws IOException {
            gen.writeStartObject();
            {
                gen.writeFieldName(NAME);
                gen.writeStartObject();
                {
                    gen.writeFieldName(value.getName());
                    gen.writeStartObject();

                    if (value.getGte() != null) {
                        provider.defaultSerializeField("gte", value.getGte(), gen);
                    }
                    if (value.getLte() != null) {
                        provider.defaultSerializeField("lte", value.getLte(), gen);
                    }
                    if (value.getGt() != null) {
                        provider.defaultSerializeField("gt", value.getGt(), gen);
                    }
                    if (value.getLt() != null) {
                        provider.defaultSerializeField("lt", value.getLt(), gen);
                    }
                    if (value.getBoost() != null) {
                        provider.defaultSerializeField("boost", value.getBoost(), gen);
                    }

                    gen.writeEndObject();
                }
                gen.writeEndObject();
            }
            gen.writeEndObject();
        }
    }
}
