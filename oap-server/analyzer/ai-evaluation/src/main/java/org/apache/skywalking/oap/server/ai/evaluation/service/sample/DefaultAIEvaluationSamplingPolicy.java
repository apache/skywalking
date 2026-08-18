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

package org.apache.skywalking.oap.server.ai.evaluation.service.sample;

import com.google.common.hash.Hashing;
import java.nio.charset.StandardCharsets;

public class DefaultAIEvaluationSamplingPolicy implements AIEvaluationSamplingPolicy {
    private static final long HASH_SPACE = 1_000_000L;
    private final int sampleRate;

    public DefaultAIEvaluationSamplingPolicy(final int sampleRate) {
        this.sampleRate = sampleRate;
    }

    @Override
    public boolean shouldSample(final String traceId) {
        final long hash = Hashing.murmur3_128()
                                 .hashString(traceId, StandardCharsets.UTF_8)
                                 .asLong();
        return Math.floorMod(hash, HASH_SPACE) < sampleRate;
    }
}
