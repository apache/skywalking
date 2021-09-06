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
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@JsonSerialize(using = Sorts.Serializer.class)
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
final class Sorts implements Iterable<Sort> {
    private final ImmutableList<Sort> sorts;

    @Override
    public Iterator<Sort> iterator() {
        if (sorts == null) {
            return Collections.emptyIterator();
        }
        return sorts.iterator();
    }

    static class Serializer extends JsonSerializer<Sorts> {
        @Override
        public void serialize(final Sorts value, final JsonGenerator gen,
                              final SerializerProvider provider)
            throws IOException {

            gen.writeStartArray();
            {
                for (final Sort sort : value) {
                    gen.writeStartObject();
                    {
                        gen.writeFieldName(sort.getName());
                        gen.writeStartObject();
                        {
                            gen.writeStringField("order", sort.getOrder().toString());
                        }
                        gen.writeEndObject();
                    }
                    gen.writeEndObject();
                }
            }
            gen.writeEndArray();
        }
    }
}
