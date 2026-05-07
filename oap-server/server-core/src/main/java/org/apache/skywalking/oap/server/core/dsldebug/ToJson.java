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

package org.apache.skywalking.oap.server.core.dsldebug;

/**
 * Implemented by every type whose state is captured into dsl-debugging
 * sample payloads — OAL {@code ISource} implementations, MAL
 * {@code SampleFamily}, LAL {@code ExecutionContext}, LAL output
 * builders, and any custom input/output type a receiver plugin wants to
 * expose.
 *
 * <p>Each class hand-builds its JSON via direct typed getters — no
 * runtime reflection, no Gson type-walk. Probe call sites invoke
 * {@link #toJson} once per sample; the recorder stores the returned
 * string verbatim and the REST handler splices it inline into the
 * response. Hot-path cost is one virtual call + the linear cost of
 * the type's own field set.
 *
 * <p>The only exemption from the "no reflection" rule is generated
 * protobuf {@code Message} types: the LAL native dispatcher uses
 * {@code com.google.protobuf.util.JsonFormat} for them, which walks
 * the proto descriptor (a structural, compile-time form, not Java
 * reflection). Anything that is not a proto message and is not
 * {@code LogData} must implement this interface to participate in
 * dsl-debugging captures.
 *
 * <p>Implementers should:
 * <ul>
 *   <li>emit valid JSON (object form, e.g. {@code "{...}"})</li>
 *   <li>build the payload via Gson's {@code JsonObject} field-by-field —
 *       library-handled string escaping with no runtime reflection</li>
 *   <li>limit to the fields an operator actually needs (skip cache /
 *       bookkeeping state)</li>
 *   <li>return {@code "{}"} when state is unavailable rather than
 *       throwing — the probe site already gates on {@code isGateOn()}
 *       so an exception here would crash the receiver thread</li>
 * </ul>
 */
public interface ToJson {
    /**
     * Build a JSON object string representing this instance's debug
     * payload. Called from the probe call site via
     * {@code addSample(..., source.toJson())}.
     *
     * @return non-null JSON object string; {@code "{}"} for empty state
     */
    String toJson();
}
