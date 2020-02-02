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

import org.apache.skywalking.oap.server.core.query.entity.ProfileAnalyzation;
import org.apache.skywalking.oap.server.core.query.entity.ProfileStackElement;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Collectors;

/**
 * Analyze {@link ProfileStack} data to {@link ProfileAnalyzation}
 *
 * @author MrPro
 */
public class ProfileAnalyzer {

    /**
     * Analyze records
     * @param stacks
     * @return
     */
    public static ProfileAnalyzation analyze(List<ProfileStack> stacks) {
        if (CollectionUtils.isEmpty(stacks)) {
            return null;
        }

        // using parallel stream
        Map<String, ProfileStackElement> stackTrees = stacks.parallelStream()
                // stack list cannot be empty
                .filter(s -> CollectionUtils.isNotEmpty(s.getStack()))
                .collect(Collectors.groupingBy(s -> s.getStack().get(0), new Collector<ProfileStack, ProfileStackNode, ProfileStackElement>() {
            @Override
            public Supplier<ProfileStackNode> supplier() {
                return ProfileStackNode::createEmptyNode;
            }

            @Override
            public BiConsumer<ProfileStackNode, ProfileStack> accumulator() {
                return (node, stack) -> node.accumulateFrom(stack);
            }

            @Override
            public BinaryOperator<ProfileStackNode> combiner() {
                return (node1, node2) -> node1.combine(node2);
            }

            @Override
            public Function<ProfileStackNode, ProfileStackElement> finisher() {
                return ProfileStackNode::buildAnalyzeResult;
            }

            @Override
            public Set<Characteristics> characteristics() {
                return Collections.unmodifiableSet(EnumSet.of(Characteristics.CONCURRENT, Characteristics.UNORDERED));
            }
        }));

        ProfileAnalyzation analyzer = new ProfileAnalyzation();
        analyzer.setStack(new ArrayList<>(stackTrees.values()));
        return analyzer;
    }

}
