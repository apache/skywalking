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
import org.apache.skywalking.banyandb.Query;
import org.apache.skywalking.banyandb.Schema;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

public class BanyanDBSchema {
    public static final Schema.TraceSeries SCHEMA;

    private static int DurationIndex;
    private static int TraceStateIndex;
    private static Schema.FieldSpec.FieldType TraceStateType;
    private static int valIntError;
    private static String valStringError;

    private static Set<String> fields = new HashSet<>();

    static {
        // read schema
        Schema.TraceSeries _schema;
        try {
            InputStream schemaFile = BanyanDBStorageProvider.class.getClassLoader().getResourceAsStream("trace_series.textproto");
            if (schemaFile != null) {
                _schema = TextFormat.parse(CharStreams.toString(new InputStreamReader(schemaFile, StandardCharsets.UTF_8)), Schema.TraceSeries.class);
            } else {
                _schema = null;
            }
        } catch (IOException ignore) {
            _schema = null;
        }
        if (_schema == null) {
            throw new RuntimeException("cannot find schema");
        }
        SCHEMA = _schema;
        for (int i = 0; i < SCHEMA.getFieldsCount(); i++) {
            final Schema.FieldSpec spec = SCHEMA.getFields(i);
            fields.add(spec.getName());
            if (SCHEMA.getReservedFieldsMap().getState().getField().equals(spec.getName())) {
                TraceStateIndex = i;
                TraceStateType = spec.getType();
            } else if ("duration".equals(spec.getName())) {
                DurationIndex = i;
            }
        }
        switch (TraceStateType) {
            case FIELD_TYPE_INT:
                valIntError = Integer.parseInt(SCHEMA.getReservedFieldsMap().getState().getValError());
                break;
            case FIELD_TYPE_STRING:
                valStringError = SCHEMA.getReservedFieldsMap().getState().getValError();
                break;
            default:
                throw new RuntimeException("invalid traceState type");
        }
    }

    public static boolean isErrorEntity(Query.Entity entity) {
        Query.TypedPair pair = entity.getFields(TraceStateIndex);
        switch (TraceStateType) {
            case FIELD_TYPE_INT:
                return pair.getIntPair().getValues(0) == valIntError;
            case FIELD_TYPE_STRING:
            default:
                return valStringError.equals(pair.getStrPair().getValues(0));
        }
    }

    public static int getDuration(Query.Entity entity) {
        return (int) entity.getFields(DurationIndex).getIntPair().getValues(0);
    }

    public static Set<String> getFieldNames() {
        return fields;
    }
}
