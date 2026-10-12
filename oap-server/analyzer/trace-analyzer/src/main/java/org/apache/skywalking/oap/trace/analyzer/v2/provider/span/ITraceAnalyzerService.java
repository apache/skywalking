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

package org.apache.skywalking.oap.trace.analyzer.v2.provider.span;

import java.util.Map;
import org.apache.skywalking.oap.server.core.trace.SpanAnalysisResult;
import org.apache.skywalking.oap.server.core.trace.SpanMetadata;
import org.apache.skywalking.oap.server.library.module.Service;

/**
 * What a receiver calls once per span, after the phase-one {@code SpanListener}s and before conversion and
 * storage, so that metrics count what the SDK sent rather than what OAP chose to store.
 */
public interface ITraceAnalyzerService extends Service {
    /**
     * Run every TAL rule of the span's layer whose input type matches {@code span}.
     *
     * @param meta               the protocol-neutral view of the span; the rules may overwrite its identity fields
     * @param span               the wire object, an instance of one registered input type
     * @param resourceAttributes the resource attributes as strings, what {@code resource()} reads; empty for inputs
     *                           that carry no resource
     * @param scopeAttributes    the instrumentation scope's attributes, what {@code scopeAttr()} reads. Passed in
     *                           rather than read off the span because OTLP carries the scope on the enclosing
     *                           {@code ScopeSpans}; empty for inputs that have no scope
     * @return the rules' verdict on the span, {@link SpanAnalysisResult#CONTINUE} when no rule claimed it
     */
    SpanAnalysisResult doAnalysis(SpanMetadata meta,
                                  Object span,
                                  Map<String, String> resourceAttributes,
                                  Map<String, String> scopeAttributes);
}
