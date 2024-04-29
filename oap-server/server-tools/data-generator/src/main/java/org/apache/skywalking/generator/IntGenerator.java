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

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.base.Preconditions;
import lombok.Data;

@JsonDeserialize(builder = IntGenerator.Builder.class)
public final class IntGenerator implements Generator<Object, Long> {
    private final boolean limitedDomain;
    private final Long min;
    private final Long max;
    private final Integer domainSize;
    private final ThreadLocalRandom random = ThreadLocalRandom.current();
    private final Set<Long> domain = new HashSet<>();

    public IntGenerator(Builder builder) {
        min = builder.min;
        max = builder.max;
        domainSize = builder.domainSize;
        limitedDomain = builder.domainSize != null && builder.domainSize > 0;

        reset();
    }

    @Override
    public Long next(Object ignored) {
        if (!limitedDomain) {
            return next0();
        }
        return domain
            .stream()
            .skip(random.nextInt(domain.size()))
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Should never happen"));
    }

    @Override
    public void reset() {
        if (limitedDomain) {
            domain.clear();

            while (domain.size() < domainSize) {
                domain.add(next0());
            }
        }
    }

    private long next0() {
        if (min != null && max != null) {
            return random.nextLong(max - min + 1) + min; // Might overflow, but it's not worthy to check
        }
        if (min != null) {
            return Math.abs(random.nextLong()) + min;
        }
        return random.nextLong(max);
    }

    @Override
    public String toString() {
        return String.valueOf(next(null));
    }

    @Data
    public static class Builder {
        private Long min;
        private Long max;
        private Integer domainSize;

        public IntGenerator build() {
            if (min != null && max != null) {
                Preconditions.checkArgument(min <= max, "min must be <= max");
                if (domainSize != null) {
                    Preconditions.checkArgument(domainSize <= max - min,
                        "domain size must be <= max - min");
                }
            }
            return new IntGenerator(this);
        }
    }
}
