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
 * as JSON content. The serialization is deferred to {@link #toLog()} so that
 * it only happens when the log is actually persisted (after LAL filtering).
 *
 * <p>The {@link #init} method stores the access log entry for JSON
 * serialization, then delegates to the base class for metadata-only
 * field population (no LogData in the envoy path).
 */
public class EnvoyAccessLogBuilder extends LogBuilder {
    public static final String NAME = "EnvoyAccessLog";

    private Object accessLogEntry;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void init(final LogMetadata metadata, final Object input,
                     final ModuleManager moduleManager) {
        if (input != null) {
            this.accessLogEntry = input;
        }
        ensureInitialized(moduleManager);
        initFromMetadata(metadata);
    }

    @Override
    @SneakyThrows
    public Log toLog() {
        final Log log = super.toLog();
        if (log.getContent() == null && accessLogEntry != null) {
            log.setContentType(ContentType.JSON);
            log.setContent(ProtoBufJsonUtils.toJSON((Message) accessLogEntry));
        }
        return log;
    }
}
