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

package org.apache.skywalking.library.elasticsearch.requests.factory.v6.codec;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import java.io.IOException;
import org.apache.skywalking.library.elasticsearch.response.Mappings;

final class V6MappingsSerializer extends JsonSerializer<Mappings> {

    @Override
    public void serialize(final Mappings value, final JsonGenerator gen,
                          final SerializerProvider serializers)
        throws IOException {
        gen.writeStartObject();
        {
            gen.writeFieldName(value.getType());
            gen.writeStartObject();
            {
                if (value.getSource() != null && !value.getSource().getExcludes().isEmpty()) {
                    gen.writeObjectField("_source", value.getSource());
                }
                gen.writeObjectField("properties", value.getProperties());
            }
            gen.writeEndObject();
        }
        gen.writeEndObject();
    }
}
