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
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.base.Preconditions;
import lombok.Data;
import lombok.experimental.Accessors;

@JsonDeserialize(builder = SequenceGenerator.Builder.class)
public final class SequenceGenerator implements Generator<Object, Long> {
    private final boolean limitedDomain;
    private final long min;
    private final long max;
    private final long step;
    private final Integer fluctuation;
    private final Integer domainSize;
    private final Random random = ThreadLocalRandom.current();
    private final Set<Long> domain = new HashSet<>();
    private volatile Long last;

    public SequenceGenerator(Builder builder) {
        min = builder.min;
        max = builder.max;
        step = builder.step;
        fluctuation = builder.fluctuation;
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

    private synchronized long next0() {
        long next = last == null ? min : last + step;

        if (fluctuation != null) {
            int j = random.nextInt(fluctuation);
            next += random.nextBoolean() ? j : -j;
        }

        if (next > max) {
            return max;
        }
        if (next < min) {
            return min;
        }

        return last = next;
    }

    @Override
    public void reset() {
        last = null;
        if (limitedDomain) {
            domain.clear();
            while (domain.size() < domainSize) {
                domain.add(next0());
            }
        }
    }

    @Override
    public String toString() {
        return String.valueOf(next(null));
    }

    @Data
    @Accessors(chain = true)
    public static class Builder {
        private long min = Long.MIN_VALUE;
        private long max = Long.MAX_VALUE;
        private long step = 1;
        private Integer domainSize;
        private Integer fluctuation;

        public SequenceGenerator build() {
            if (domainSize != null) {
                Preconditions.checkArgument(domainSize > 0, "domainSize must be > 0");
                Preconditions.checkArgument(domainSize + min <= max,
                    "domain size must be <= max - min");
            }
            return new SequenceGenerator(this);
        }
    }
}
