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

package org.apache.skywalking.oap.meter.analyzer.v2.dsl;

import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterEntity;

/**
 * Functional interfaces used as parameters in {@link SampleFamily} methods.
 */
public final class SampleFamilyFunctions {

    private SampleFamilyFunctions() {
    }

    /**
     * Receives a mutable label map and returns the (possibly modified) map.
     */
    @FunctionalInterface
    public interface TagFunction extends Function<Map<String, String>, Map<String, String>> {
    }

    /**
     * Tests whether a sample's labels match the filter criteria.
     */
    @FunctionalInterface
    public interface SampleFilter extends Predicate<Map<String, String>> {
    }

    /**
     * Called for each element in the array with the element value and a mutable labels map.
     */
    @FunctionalInterface
    public interface ForEachFunction {
        void accept(String element, Map<String, String> tags);
    }

    /**
     * Decorates service meter entities.
     */
    @FunctionalInterface
    public interface DecorateFunction extends Consumer<MeterEntity> {
    }

    /**
     * Extracts instance properties from sample labels.
     */
    @FunctionalInterface
    public interface PropertiesExtractor extends Function<Map<String, String>, Map<String, String>> {
    }
}
