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

package org.apache.skywalking.banyandb.v1.client;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import org.apache.skywalking.banyandb.v1.trace.BanyandbTrace;

/**
 * RowEntity represents an entity of BanyanDB entity.
 */
@Getter
public class RowEntity {
    /**
     * identity of the entity.
     * For a trace entity, it is the spanID of a Span or the segmentId of a segment in Skywalking.
     */
    private final String id;

    /**
     * timestamp of the entity in the timeunit of milliseconds.
     */
    private final long timestamp;

    /**
     * binary part of the entity
     */
    private final byte[] binary;

    /**
     * fields are indexed-fields that are searchable in BanyanBD
     */
    private final List<FieldAndValue<?>> fields;

    RowEntity(BanyandbTrace.Entity entity) {
        id = entity.getEntityId();
        timestamp = entity.getTimestamp().getSeconds() * 1000 + entity.getTimestamp().getNanos() / 1000 / 1000;
        binary = entity.getDataBinary().toByteArray();
        fields = new ArrayList<>(entity.getFieldsCount());
        entity.getFieldsList().forEach(field -> fields.add(FieldAndValue.build(field)));
    }
}
