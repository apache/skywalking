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

package org.apache.skywalking.oap.server.core.profile.analyze;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;
import org.apache.skywalking.oap.server.core.query.type.ProfileStackTree;

/**
 * Work for {@link ProfileAnalyzer} to analyze.
 */
public class ProfileAnalyzeCollector implements Collector<ProfileStack, ProfileStackNode, ProfileStackTree> {
    @Override
    public Supplier<ProfileStackNode> supplier() {
        return ProfileStackNode::newNode;
    }

    @Override
    public BiConsumer<ProfileStackNode, ProfileStack> accumulator() {
        return ProfileStackNode::accumulateFrom;
    }

    @Override
    public BinaryOperator<ProfileStackNode> combiner() {
        return ProfileStackNode::combine;
    }

    @Override
    public Function<ProfileStackNode, ProfileStackTree> finisher() {
        return ProfileStackNode::buildAnalyzeResult;
    }

    @Override
    public Set<Characteristics> characteristics() {
        return Collections.unmodifiableSet(EnumSet.of(Characteristics.CONCURRENT, Characteristics.UNORDERED));
    }
}
