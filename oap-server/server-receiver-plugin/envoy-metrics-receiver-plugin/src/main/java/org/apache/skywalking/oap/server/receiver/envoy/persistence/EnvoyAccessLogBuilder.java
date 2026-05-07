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

package org.apache.skywalking.oap.server.receiver.envoy.persistence;

import com.google.gson.JsonObject;
import com.google.protobuf.Message;
import lombok.SneakyThrows;
import org.apache.skywalking.oap.server.core.query.type.ContentType;
import org.apache.skywalking.oap.server.core.source.Log;
import org.apache.skywalking.oap.server.core.source.LogBuilder;
import org.apache.skywalking.oap.server.core.source.LogMetadata;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.util.ProtoBufJsonUtils;

/**
 * LAL output builder for envoy access logs (both HTTP and TCP).
 *
 * <p>Extends {@link LogBuilder} to serialize the raw protobuf access log entry
 * as JSON content. The serialization is converted once on {@link #init} into
 * a cached string so the production sink path ({@link #toLog()}) and the
 * dsl-debugging dump path ({@link #appendBodyContent}) both read it without
 * re-walking the proto descriptor.
 */
public class EnvoyAccessLogBuilder extends LogBuilder {
    public static final String NAME = "EnvoyAccessLog";

    /**
     * Proto-as-JSON form of the access log entry passed to {@link #init},
     * computed once and reused by {@link #toLog()} (production path) and
     * {@link #appendBodyContent} (debug path). The cache means
     * dsl-debugging captures fired on every probe stage don't re-run
     * {@code JsonFormat} per sample — the proto's descriptor walk
     * happens at most once per log.
     */
    private String accessLogEntryJson;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    @SneakyThrows
    public void bindInput(final LogMetadata metadata, final Object input) {
        if (input != null) {
            this.accessLogEntryJson = ProtoBufJsonUtils.toJSON((Message) input);
        }
        // Envoy ALS doesn't deliver LogData, so skip super.bindInput's LogData
        // cast branch and just run the metadata-derived field population.
        initFromMetadata(metadata);
    }

    @Override
    @SneakyThrows
    public void init(final LogMetadata metadata, final Object input,
                     final ModuleManager moduleManager) {
        ensureInitialized(moduleManager);
        bindInput(metadata, input);
    }

    @Override
    public Log toLog() {
        final Log log = super.toLog();
        if (log.getContent() == null && accessLogEntryJson != null) {
            log.setContentType(ContentType.JSON);
            log.setContent(accessLogEntryJson);
        }
        return log;
    }

    /**
     * Mirrors {@link #toLog()}'s content substitution: when the base
     * {@code logData} body is empty (the envoy ALS path, where the proto
     * is the only payload), the DB-bound {@code content} field is the
     * proto JSON with {@code contentType=JSON}. Reads from the cache —
     * no per-call descriptor walk.
     */
    @Override
    protected void appendBodyContent(final JsonObject obj) {
        super.appendBodyContent(obj);
        if (!obj.has("content") && accessLogEntryJson != null) {
            obj.addProperty("contentType", ContentType.JSON.name());
            obj.addProperty("content", accessLogEntryJson);
        }
    }
}
