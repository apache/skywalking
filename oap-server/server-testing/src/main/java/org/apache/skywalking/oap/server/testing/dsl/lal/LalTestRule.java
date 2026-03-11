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

package org.apache.skywalking.oap.server.testing.dsl.lal;

import java.io.File;
import java.util.List;
import java.util.Map;
import lombok.Getter;

/**
 * A parsed LAL rule from a YAML config file, with optional test input data.
 */
@Getter
public final class LalTestRule {
    private final String name;
    private final String dsl;
    private final String inputType;
    private final String outputType;
    private final String layer;
    private final boolean v2Only;
    private final List<Map<String, Object>> inputs;
    private final File sourceFile;
    private final int lineNo;

    public LalTestRule(final String name, final String dsl,
                       final String inputType, final String outputType,
                       final String layer, final boolean v2Only,
                       final List<Map<String, Object>> inputs,
                       final File sourceFile, final int lineNo) {
        this.name = name;
        this.dsl = dsl;
        this.inputType = inputType;
        this.outputType = outputType;
        this.layer = layer;
        this.v2Only = v2Only;
        this.inputs = inputs;
        this.sourceFile = sourceFile;
        this.lineNo = lineNo;
    }
}
