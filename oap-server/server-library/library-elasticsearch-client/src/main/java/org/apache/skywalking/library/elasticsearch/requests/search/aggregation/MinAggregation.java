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

package org.apache.skywalking.library.elasticsearch.requests.search.aggregation;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.io.IOException;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
@JsonSerialize(using = MinAggregation.Serializer.class)
public final class MinAggregation extends Aggregation {
    private final String name;
    private final String field;

    @Override
    public String name() {
        return name;
    }

    static final class Serializer extends JsonSerializer<MinAggregation> {
        @Override
        public void serialize(final MinAggregation value, final JsonGenerator gen,
                              final SerializerProvider provider) throws IOException {
            gen.writeStartObject();
            {
                gen.writeFieldName("min");
                gen.writeStartObject();
                {
                    gen.writeStringField("field", value.getField());
                }
                gen.writeEndObject();
            }
            gen.writeEndObject();
        }
    }
}
