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

package org.apache.skywalking.oap.trace.analyzer.v2.dsl;

import org.apache.skywalking.oap.server.core.source.TALFunctionProvider;

/**
 * Serves for the native input a name {@link TestFunctionProvider} serves for every input, which overlaps there.
 */
public class TestNativeTwiceProvider implements TALFunctionProvider {
    @Override
    public String module() {
        return "native-twice";
    }

    @Override
    public boolean supportsInputType(final String inputType) {
        return TALFunctionProvider.SW_NATIVE_INPUT_TYPE.equals(inputType);
    }

    public long twice(final long value) {
        return value + value;
    }
}
