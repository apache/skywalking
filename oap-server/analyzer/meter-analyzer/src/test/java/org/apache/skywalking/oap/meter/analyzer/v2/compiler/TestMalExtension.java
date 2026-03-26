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
 */

package org.apache.skywalking.oap.meter.analyzer.v2.compiler;

import org.apache.skywalking.oap.meter.analyzer.v2.dsl.SampleFamily;
import org.apache.skywalking.oap.meter.analyzer.v2.spi.MALContextFunction;
import org.apache.skywalking.oap.meter.analyzer.v2.spi.MalFunctionExtension;

/**
 * Test-only MAL extension for verifying the SPI framework.
 */
public class TestMalExtension implements MalFunctionExtension {

    @Override
    public String name() {
        return "test";
    }

    @MALContextFunction
    public static SampleFamily scale(final SampleFamily sf, final double factor) {
        return sf.multiply(Double.valueOf(factor));
    }

    @MALContextFunction
    public static SampleFamily noop(final SampleFamily sf) {
        return sf;
    }
}
