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

package org.apache.skywalking.oap.server.receiver.envoy;

import io.envoyproxy.envoy.data.accesslog.v3.HTTPAccessLogEntry;
import org.apache.skywalking.oap.log.analyzer.v2.spi.LALSourceTypeProvider;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.receiver.envoy.persistence.EnvoyAccessLogBuilder;

/**
 * Declares {@link HTTPAccessLogEntry} as the extra log type for the
 * {@link Layer#MESH} layer, enabling the LAL compiler to generate direct
 * proto getter calls for envoy access log rules.
 *
 * <p>Also declares {@link EnvoyAccessLogBuilder} as the default output type,
 * so that the raw access log entry is serialized as JSON content only when
 * the log is actually persisted (after LAL filtering).
 */
public class EnvoyHTTPLALSourceTypeProvider implements LALSourceTypeProvider {
    @Override
    public Layer layer() {
        return Layer.MESH;
    }

    @Override
    public Class<?> inputType() {
        return HTTPAccessLogEntry.class;
    }

    @Override
    public Class<?> outputType() {
        return EnvoyAccessLogBuilder.class;
    }
}
