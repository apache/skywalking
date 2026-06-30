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

package org.apache.skywalking.oap.log.analyzer.v2.spi;

import com.google.protobuf.Message;

/**
 * SPI for receiver plugins to render an operator-readable debug JSON for a
 * typed LAL input that the generic
 * {@link com.google.protobuf.util.JsonFormat} printer cannot render
 * meaningfully.
 *
 * <p>The motivating case is Envoy ALS: its
 * {@code common_properties.filter_state_objects} carry the Istio
 * metadata-exchange peer as a FlatBuffer wrapped in a
 * {@code google.protobuf.BytesValue} (legacy Wasm form) or a
 * {@code google.protobuf.Struct} (modern native form). The generic printer
 * can at best emit opaque base64 for the FlatBuffer; a renderer decodes it
 * into the readable peer metadata, reusing the receiver's own decoders.
 *
 * <p>Why an SPI rather than a direct call: the dispatch happens in
 * {@code log-analyzer}, which the receiver plugins depend on (never the
 * reverse), so {@code log-analyzer} cannot import a receiver's decoder. The
 * receiver registers an implementation here, discovered via
 * {@link java.util.ServiceLoader}, mirroring the existing
 * {@link LALSourceTypeProvider} seam. Implementations register in
 * {@code META-INF/services/org.apache.skywalking.oap.log.analyzer.v2.spi.LalInputDebugRenderer}.
 */
public interface LalInputDebugRenderer {

    /**
     * The proto input type this renderer handles, e.g.
     * {@code HTTPAccessLogEntry}. Matched against the input's runtime class.
     *
     * @return the concrete {@link Message} subtype this renderer renders.
     */
    Class<? extends Message> inputType();

    /**
     * Render an operator-readable JSON object string for {@code input}.
     * Must not throw — return {@code null} on any failure so the framework
     * falls back to its generic protobuf printer.
     *
     * @param input the LAL input message to render.
     * @return a JSON object string, or {@code null} to fall back.
     */
    String render(Message input);
}
