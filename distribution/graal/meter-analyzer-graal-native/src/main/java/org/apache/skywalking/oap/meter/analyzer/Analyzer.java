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

package org.apache.skywalking.oap.meter.analyzer;

import com.google.common.collect.ImmutableMap;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import org.apache.skywalking.oap.meter.analyzer.dsl.SampleFamily;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterSystem;

/**
 * The old logic of this class used Groovy;
 * however, GraalVM does not support it well, so it has been replaced with an empty implementation.
 * In the future, other methods will be used to implement this class.
 */
public class Analyzer {

    public static final Tuple2<String, SampleFamily> NIL = Tuple.of("", null);

    public static Analyzer build(final String metricName,
                                 final String filterExpression,
                                 final String expression,
                                 final MeterSystem meterSystem) {

        return new Analyzer();
    }

    public void analyse(final ImmutableMap<String, SampleFamily> sampleFamilies) {

    }
}
