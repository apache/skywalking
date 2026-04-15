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

package org.apache.skywalking.oap.server.core.trace;

import java.util.Collections;
import java.util.Map;
import lombok.Builder;
import lombok.Getter;

/**
 * Result returned by {@link SpanListener} phase methods.
 *
 * <p>Controls only persistence and tag injection. Listeners emit sources
 * (OAL, MAL, logs) directly via their cached {@code ModuleManager} references —
 * the manager does not handle source dispatch.
 */
@Getter
@Builder
public class SpanListenerResult {
    /**
     * No-op result: persist the span, no modifications.
     */
    public static final SpanListenerResult CONTINUE = SpanListenerResult.builder().build();

    /**
     * Whether this span should be persisted as a trace record. Default: true.
     * If any listener returns false, the span is not persisted.
     */
    @Builder.Default
    private final boolean shouldPersist = true;

    /**
     * Additional tags to inject into the span before persistence.
     * Merged into resource tags (phase 1) or ZipkinSpan tags (phase 2).
     */
    @Builder.Default
    private final Map<String, String> additionalTags = Collections.emptyMap();
}
