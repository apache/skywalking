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

package org.apache.skywalking.oap.log.analyzer.dsl.spec.parser;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.skywalking.oap.log.analyzer.provider.LogAnalyzerModuleConfig;
import org.apache.skywalking.oap.server.library.module.ModuleManager;

public class JsonParserSpec extends AbstractParserSpec {
    private final GsonBuilder gsonBuilder;

    private final Gson gson;

    public JsonParserSpec(final ModuleManager moduleManager,
                          final LogAnalyzerModuleConfig moduleConfig) {
        super(moduleManager, moduleConfig);

        gsonBuilder = new GsonBuilder();

        // We just create a gson instance in advance for now (for the sake of performance),
        // when we want to provide some extra options, we'll move this into method "create" then.
        gson = gsonBuilder.create();
    }

    public Gson create() {
        return gson;
    }
}
