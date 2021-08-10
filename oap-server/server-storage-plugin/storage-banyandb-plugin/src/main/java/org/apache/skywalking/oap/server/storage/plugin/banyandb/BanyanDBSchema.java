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

package org.apache.skywalking.oap.server.storage.plugin.banyandb;

import com.google.common.io.CharStreams;
import com.google.protobuf.TextFormat;
import org.apache.skywalking.banyandb.Database;
import org.apache.skywalking.banyandb.Query;
import org.apache.skywalking.banyandb.Schema;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class BanyanDBSchema {
    private final Schema.TraceSeries traceSeries;
    private final Set<String> fields = new LinkedHashSet<>();
    private final Map<String, Integer> fieldsIndexMapping = new HashMap<>();

    private int traceStateIndex;
    private int traceIdIndex;
    private Schema.FieldSpec.FieldType traceStateType;
    private int valIntError;
    private String valStringError;

    public static BanyanDBSchema fromTextProtoResource(String filename) {
        // read schema
        Schema.TraceSeries schema;
        try {
            InputStream schemaFile = BanyanDBStorageProvider.class.getClassLoader().getResourceAsStream(filename);
            if (schemaFile != null) {
                schema = TextFormat.parse(CharStreams.toString(new InputStreamReader(schemaFile, StandardCharsets.UTF_8)), Schema.TraceSeries.class);
            } else {
                schema = null;
            }
        } catch (IOException ignore) {
            schema = null;
        }
        if (schema == null) {
            throw new RuntimeException("cannot find schema");
        }
        return new BanyanDBSchema(schema);
    }

    private BanyanDBSchema(Schema.TraceSeries traceSeries) {
        this.traceSeries = traceSeries;
        // parse fields in strict order
        for (int i = 0; i < this.traceSeries.getFieldsCount(); i++) {
            final Schema.FieldSpec spec = this.traceSeries.getFields(i);
            fields.add(spec.getName());
            if (this.traceSeries.getReservedFieldsMap().getState().getField().equals(spec.getName())) {
                traceStateIndex = i;
                traceStateType = spec.getType();
            } else if (this.traceSeries.getReservedFieldsMap().getTraceId().equals(spec.getName())) {
                traceIdIndex = i;
            } else {
                fieldsIndexMapping.put(spec.getName(), i);
            }
        }
        if (traceStateType == null) {
            throw new IllegalStateException("state field is not defined");
        }
        switch (traceStateType) {
            case FIELD_TYPE_INT:
                valIntError = Integer.parseInt(this.traceSeries.getReservedFieldsMap().getState().getValError());
                break;
            case FIELD_TYPE_STRING:
                valStringError = this.traceSeries.getReservedFieldsMap().getState().getValError();
                break;
            default:
                throw new RuntimeException("invalid traceState type");
        }
    }

    public boolean isErrorEntity(Query.Entity entity) {
        Query.TypedPair pair = entity.getFields(this.traceStateIndex);
        switch (this.traceStateType) {
            case FIELD_TYPE_INT:
                return pair.getIntPair().getValues(0) == valIntError;
            case FIELD_TYPE_STRING:
            default:
                return valStringError.equals(pair.getStrPair().getValues(0));
        }
    }

    Query.TypedPair getField(Query.Entity entity, String key) {
        if (entity == null) {
            return null;
        }
        final int fieldIndex = this.fieldsIndexMapping.getOrDefault(key, -1);
        if (fieldIndex < 0 || entity.getFieldsCount() <= fieldIndex) {
            return null;
        }
        return entity.getFields(fieldIndex);
    }

    public int getDuration(Query.Entity entity) {
        Query.TypedPair pair = this.getField(entity, "duration");
        if (pair == null) {
            return 0;
        }
        return (int) pair.getIntPair().getValues(0);
    }

    public String getTraceId(Query.Entity entity) {
        Query.TypedPair pair = entity.getFields(this.traceIdIndex);
        return pair.getStrPair().getValues(0);
    }

    public String getEndpointName(Query.Entity entity) {
        Query.TypedPair pair = this.getField(entity, "endpoint_name");
        if (pair == null) {
            return null;
        }
        return pair.getStrPair().getValues(0);
    }

    public long getStartTime(Query.Entity entity) {
        Query.TypedPair pair = this.getField(entity, "start_time");
        if (pair == null) {
            return 0L;
        }
        return pair.getIntPair().getValues(0);
    }

    public Set<String> getFieldNames() {
        return this.fields;
    }

    public Database.Metadata getMetadata() {
        return this.traceSeries.getMetadata();
    }
}
