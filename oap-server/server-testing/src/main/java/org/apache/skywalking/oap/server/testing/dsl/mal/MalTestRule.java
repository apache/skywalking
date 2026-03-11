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

package org.apache.skywalking.oap.server.testing.dsl.mal;

import java.io.File;
import java.util.Map;
import lombok.Getter;

/**
 * A parsed MAL rule from a YAML config file, with optional test input data.
 */
@Getter
public final class MalTestRule {
    private final String name;
    private final String fullExpression;
    private final String filter;
    private final String metricPrefix;
    private final Map<String, Object> inputConfig;
    private final String dirName;
    private final File sourceFile;
    private final int lineNo;

    public MalTestRule(final String name, final String fullExpression,
                       final String filter, final String metricPrefix,
                       final Map<String, Object> inputConfig,
                       final String dirName,
                       final File sourceFile, final int lineNo) {
        this.name = name;
        this.fullExpression = fullExpression;
        this.filter = filter;
        this.metricPrefix = metricPrefix;
        this.inputConfig = inputConfig;
        this.dirName = dirName;
        this.sourceFile = sourceFile;
        this.lineNo = lineNo;
    }
}
