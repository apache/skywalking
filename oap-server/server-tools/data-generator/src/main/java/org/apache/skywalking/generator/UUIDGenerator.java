/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.skywalking.generator;

import static com.google.common.base.Preconditions.checkArgument;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Data;

@JsonDeserialize(builder = UUIDGenerator.Builder.class)
public final class UUIDGenerator implements Generator<String> {
    private final int changingFrequency;
    private final AtomicInteger counter;
    private final AtomicReference<String> last =
        new AtomicReference<>(UUID.randomUUID().toString());

    public UUIDGenerator(Builder builder) {
        checkArgument(builder.changingFrequency > 0, "changingFrequency must be greater than 0");
        changingFrequency = builder.changingFrequency;
        counter = new AtomicInteger();
        reset();
    }

    @Override
    public String next() {
        if (counter.incrementAndGet() < changingFrequency) {
            return last.get();
        }
        reset();
        return last.getAndSet(UUID.randomUUID().toString());
    }

    @Override
    public String toString() {
        return String.valueOf(next());
    }

    @Override
    public void reset() {
        counter.set(0);
    }

    @Data
    public static class Builder {
        private int changingFrequency = 1;

        public UUIDGenerator build() {
            return new UUIDGenerator(this);
        }
    }
}
