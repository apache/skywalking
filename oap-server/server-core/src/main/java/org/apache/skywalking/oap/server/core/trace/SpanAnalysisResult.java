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
 * What the TAL rules of one span decided: whether the span is still worth storing, whether it is stored regardless
 * of the receiver's head sampling, and the tags the rules injected into it.
 *
 * <p>Merged with {@link SpanListenerResult} the way the listeners merge among themselves: any veto drops the span,
 * tags merge last-writer-wins.
 */
@Getter
@Builder
public class SpanAnalysisResult {
    /**
     * No rule voted: persist the span, inject nothing, enforce nothing.
     */
    public static final SpanAnalysisResult CONTINUE = SpanAnalysisResult.builder().build();

    /**
     * Whether every rule that voted said keep. A rule that aborted did not vote.
     */
    @Builder.Default
    private final boolean persist = true;

    /**
     * Whether an {@code enforcer {}} fired. It asks the forward to skip the trace-id head sampling for this span;
     * it does not lift the receiver's rate limit, which stays the operator's ceiling.
     */
    @Builder.Default
    private final boolean enforced = false;

    /**
     * Tags the rules appended to the span before it is persisted.
     */
    @Builder.Default
    private final Map<String, String> additionalTags = Collections.emptyMap();
}
