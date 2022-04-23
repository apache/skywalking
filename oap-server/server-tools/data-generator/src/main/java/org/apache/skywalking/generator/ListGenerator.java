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

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Data;

@JsonDeserialize(builder = ListGenerator.Builder.class)
public final class ListGenerator<T> implements Generator<List<T>> {
    private final T item;
    private final int size;

    public ListGenerator(Builder<T> builder) {
        item = builder.item;
        size = builder.size;
    }

    @Override
    public List<T> next() {
        return IntStream
            .range(0, size)
            .mapToObj($ -> item)
            .collect(Collectors.toList());
    }

    @Override
    public void reset() {
        ((Generator<?>) item).reset();
    }

    @Data
    public static class Builder<T> {
        private int size;
        private T item;

        public ListGenerator<T> build() {
            return new ListGenerator<>(this);
        }
    }
}
