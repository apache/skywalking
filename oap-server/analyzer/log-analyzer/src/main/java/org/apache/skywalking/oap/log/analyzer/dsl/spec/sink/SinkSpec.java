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

package org.apache.skywalking.oap.log.analyzer.dsl.spec.sink;

import groovy.lang.Closure;
import org.apache.skywalking.oap.log.analyzer.dsl.spec.AbstractSpec;
import org.apache.skywalking.oap.log.analyzer.provider.LogAnalyzerModuleConfig;
import org.apache.skywalking.oap.server.library.module.ModuleManager;

public class SinkSpec extends AbstractSpec {

    private final SamplerSpec sampler;

    public SinkSpec(final ModuleManager moduleManager,
                    final LogAnalyzerModuleConfig moduleConfig) {
        super(moduleManager, moduleConfig);

        sampler = new SamplerSpec(moduleManager(), moduleConfig());
    }

    @SuppressWarnings("unused")
    public void sampler(final Closure<Void> cl) {
        if (BINDING.get().shouldAbort()) {
            return;
        }
        cl.setDelegate(sampler);
        cl.call();
    }

    @SuppressWarnings("unused")
    public void enforcer(final Closure<Void> cl) {
        if (BINDING.get().shouldAbort()) {
            return;
        }
        BINDING.get().save();
    }

    @SuppressWarnings("unused")
    public void dropper(final Closure<Void> cl) {
        if (BINDING.get().shouldAbort()) {
            return;
        }
        BINDING.get().drop();
    }
}
