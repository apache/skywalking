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
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@JsonSerialize(using = TermsQuery.Serializer.class)
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public final class TermsQuery extends Query {
    private final String name;
    private final List<?> values;

    static final class Serializer extends JsonSerializer<TermsQuery> {
        static final String NAME = "terms";

        @Override
        public void serialize(final TermsQuery term, final JsonGenerator gen,
                              final SerializerProvider provider) throws IOException {
            gen.writeStartObject();
            {
                gen.writeFieldName(NAME);
                gen.writeStartObject();
                {
                    gen.writeFieldName(term.getName());
                    gen.writeObject(term.getValues());
                }
                gen.writeEndObject();
            }
            gen.writeEndObject();
        }
    }
}
