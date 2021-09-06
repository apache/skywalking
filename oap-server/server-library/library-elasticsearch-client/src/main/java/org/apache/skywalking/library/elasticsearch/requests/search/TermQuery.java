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
@JsonSerialize(using = TermQuery.Serializer.class)
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public final class TermQuery extends Query {
    private final String name;
    private final Object value;

    static final class Serializer extends JsonSerializer<TermQuery> {
        static final String NAME = "term";

        @Override
        public void serialize(final TermQuery term, final JsonGenerator gen,
                              final SerializerProvider provider) throws IOException {
            gen.writeStartObject();
            {
                gen.writeFieldName(NAME);
                gen.writeStartObject();
                {
                    gen.writeFieldName(term.getName());
                    gen.writeObject(term.getValue());
                }
                gen.writeEndObject();
            }
            gen.writeEndObject();
        }
    }
}
