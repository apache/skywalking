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
 *
 */

package org.apache.skywalking.library.banyandb.v1.client;

import com.google.protobuf.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import org.apache.skywalking.banyandb.model.v1.BanyandbModel;

/**
 * RowEntity represents an entity of BanyanDB.
 */
@Getter
public class RowEntity {
    /**
     * timestamp of the entity in the timeunit of milliseconds.
     */
    protected final long timestamp;

    /**
     * tags is a map maintaining the relation between tag name and its value,
     * (in the format of Java Types converted from gRPC Types).
     * The family name is thus ignored, since the name should be globally unique for a schema.
     */
    protected final Map<String, Object> tags;

    protected RowEntity(Timestamp ts, List<BanyandbModel.TagFamily> tagFamilyList) {
        timestamp = ts.getSeconds() * 1000 + ts.getNanos() / 1_000_000;
        this.tags = new HashMap<>();
        for (final BanyandbModel.TagFamily tagFamily : tagFamilyList) {
            for (final BanyandbModel.Tag tag : tagFamily.getTagsList()) {
                final Object val = convertToJavaType(tag.getValue());
                if (val != null) {
                    this.tags.put(tag.getKey(), val);
                }
            }
        }
    }

    public <T> T getTagValue(String tagName) {
        return (T) this.tags.get(tagName);
    }

    private Object convertToJavaType(BanyandbModel.TagValue tagValue) {
        switch (tagValue.getValueCase()) {
            case INT:
                return tagValue.getInt().getValue();
            case STR:
                return tagValue.getStr().getValue();
            case NULL:
                return null;
            case INT_ARRAY:
                return tagValue.getIntArray().getValueList();
            case STR_ARRAY:
                return tagValue.getStrArray().getValueList();
            case BINARY_DATA:
                return tagValue.getBinaryData().toByteArray();
            default:
                throw new IllegalStateException("illegal type of TagValue");
        }
    }
}
