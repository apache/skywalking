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

package org.apache.skywalking.oap.server.ai.evaluation;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.skywalking.oap.server.library.module.Service;

/**
 * The sampling decisions {@code prepare()} made, for the trace analyzer's native rules, which apply the evaluation's
 * sampling policy themselves. Nothing of the judge settings is here.
 */
@Getter
@RequiredArgsConstructor
public class AIEvaluationSamplingConfig implements Service {
    /**
     * Whether the module evaluates; false when the judge configuration is incomplete and the inactive service, which
     * samples nothing, is registered.
     */
    private final boolean active;
    /**
     * {@code sampleRate}, the traces sampled out of 1,000,000.
     */
    private final int sampleRate;
}
