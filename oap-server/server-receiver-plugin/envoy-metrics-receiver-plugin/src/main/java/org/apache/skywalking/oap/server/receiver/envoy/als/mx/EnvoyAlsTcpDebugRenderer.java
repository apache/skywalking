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

package org.apache.skywalking.oap.server.receiver.envoy.als.mx;

import com.google.protobuf.Message;
import io.envoyproxy.envoy.data.accesslog.v3.TCPAccessLogEntry;
import org.apache.skywalking.oap.log.analyzer.v2.spi.LalInputDebugRenderer;

/**
 * Bridges LAL live-debug input rendering for Envoy {@link TCPAccessLogEntry}
 * to {@link EnvoyAlsJsonUtils}. TCP access logs feed the LAL pipeline via
 * {@code TCPLogsPersistence}, and share {@code AccessLogCommon} (hence the same
 * metadata-exchange peer keys and encodings) with HTTP, so the peer in
 * {@code filter_state_objects} is shown decoded rather than as opaque base64.
 * Registered via {@code META-INF/services}; discovered by the framework's
 * {@link LalInputDebugRenderer} SPI.
 */
public class EnvoyAlsTcpDebugRenderer implements LalInputDebugRenderer {

    @Override
    public Class<? extends Message> inputType() {
        return TCPAccessLogEntry.class;
    }

    @Override
    public String render(final Message input) {
        try {
            return EnvoyAlsJsonUtils.toJSON((TCPAccessLogEntry) input);
        } catch (final Exception e) {
            return null; // fall back to the framework's generic protobuf printer
        }
    }
}
