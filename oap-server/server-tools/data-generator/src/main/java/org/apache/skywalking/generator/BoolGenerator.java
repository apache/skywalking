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

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Data;

@JsonDeserialize(builder = BoolGenerator.Builder.class)
public final class BoolGenerator implements Generator<Boolean> {
    private final Random random = ThreadLocalRandom.current();
    private final double possibility;

    public BoolGenerator(Builder builder) {
        possibility = builder.possibility;
    }

    @Override
    public Boolean next() {
        return random.nextDouble() < possibility;
    }

    @Override
    public String toString() {
        return String.valueOf(next());
    }

    @Data
    public static class Builder {
        private double possibility = .5;

        public BoolGenerator build() {
            return new BoolGenerator(this);
        }
    }
}
