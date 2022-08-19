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
import com.google.common.base.Strings;
import org.apache.commons.lang3.RandomStringUtils;
import lombok.Data;

@JsonDeserialize(builder = StringGenerator.Builder.class)
public final class StringGenerator implements Generator<String> {
    private final int length;
    private final String prefix;
    private final boolean letters;
    private final boolean numbers;
    private final boolean limitedDomain;
    private final Random random = ThreadLocalRandom.current();
    private final Set<String> domain = new HashSet<>();

    public StringGenerator(Builder builder) {
        length = builder.length;
        prefix = builder.prefix;
        letters = builder.letters;
        numbers = builder.numbers;
        limitedDomain = builder.domainSize > 0;

        if (limitedDomain) {
            while (domain.size() < builder.domainSize) {
                final String r = RandomStringUtils.random(length, letters, numbers);
                if (!Strings.isNullOrEmpty(builder.prefix)) {
                    domain.add(builder.prefix + r);
                } else {
                    domain.add(r);
                }
            }
        }
    }

    @Override
    public String next() {
        if (!limitedDomain) {
            return Strings.nullToEmpty(prefix)
                + RandomStringUtils.random(length, letters, numbers);
        }
        return domain
            .stream()
            .skip(random.nextInt(domain.size()))
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Should never happen"));
    }

    @Override
    public String toString() {
        return String.valueOf(next());
    }

    @Data
    public static class Builder {
        private int length;
        private String prefix;
        private boolean letters;
        private boolean numbers;
        private int domainSize;

        public StringGenerator build() {
            return new StringGenerator(this);
        }
    }
}
