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
import org.apache.skywalking.apm.network.logging.v3.LogData;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.query.type.ContentType;
import org.apache.skywalking.oap.server.core.source.Log;
import org.apache.skywalking.oap.server.core.source.LogBuilder;
import org.apache.skywalking.oap.server.library.util.ProtoBufJsonUtils;

/**
 * LAL output builder for envoy access logs (both HTTP and TCP).
 *
 * <p>Extends {@link LogBuilder} to serialize the raw protobuf access log entry
 * as JSON content. The serialization is deferred to {@link #toLog()} so that
 * it only happens when the log is actually persisted (after LAL filtering).
 *
 * <p>The {@link #init} method receives the raw protobuf access log entry
 * directly (e.g., {@code HTTPAccessLogEntry}) as declared by
 * {@code EnvoyHTTPLALSourceTypeProvider#inputType()}, and passes a default
 * {@code LogData} to the base class since all standard fields are populated
 * by the LAL extractor.
 */
public class EnvoyAccessLogBuilder extends LogBuilder {
    public static final String NAME = "EnvoyAccessLog";

    private Message accessLogEntry;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void init(final Object logData, final NamingControl namingControl) {
        this.accessLogEntry = (Message) logData;
        // Standard fields (service, instance, endpoint, layer, etc.) are
        // populated by the LAL extractor before init() is called.
        // Pass a default LogData to the base class so toLog() body/tag
        // accessors work safely (both will be empty for envoy).
        super.init(LogData.getDefaultInstance(), namingControl);
    }

    @Override
    @SneakyThrows
    public Log toLog() {
        final Log log = super.toLog();
        if (log.getContent() == null && accessLogEntry != null) {
            log.setContentType(ContentType.JSON);
            log.setContent(ProtoBufJsonUtils.toJSON(accessLogEntry));
        }
        return log;
    }
}
