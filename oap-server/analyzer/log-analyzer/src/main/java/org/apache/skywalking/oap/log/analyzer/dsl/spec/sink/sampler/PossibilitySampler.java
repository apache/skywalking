/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.apache.skywalking.oap.log.analyzer.dsl.spec.sink.sampler;

import io.netty.util.internal.ThreadLocalRandom;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

@RequiredArgsConstructor
@Accessors(fluent = true)
@EqualsAndHashCode(of = {"percentage"})
public class PossibilitySampler implements Sampler {
    @Getter
    private final int percentage;

    private final ThreadLocalRandom random = ThreadLocalRandom.current();

    @Override
    public PossibilitySampler start() {
        return this;
    }

    @Override
    public void close() {
    }

    @Override
    public boolean sample() {
        return random.nextInt(100) < percentage;
    }

    @Override
    public PossibilitySampler reset() {
        return this;
    }
}
