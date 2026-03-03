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

package org.apache.skywalking.oap.log.analyzer.v2.dsl.spec.sink;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.skywalking.oap.log.analyzer.v2.dsl.ExecutionContext;
import org.apache.skywalking.oap.log.analyzer.v2.dsl.spec.AbstractSpec;
import org.apache.skywalking.oap.log.analyzer.v2.dsl.spec.sink.sampler.RateLimitingSampler;
import org.apache.skywalking.oap.log.analyzer.v2.dsl.spec.sink.sampler.Sampler;
import org.apache.skywalking.oap.log.analyzer.v2.provider.LogAnalyzerModuleConfig;
import org.apache.skywalking.oap.server.library.module.ModuleManager;

public class SamplerSpec extends AbstractSpec {
    private final Map<String, Sampler> rateLimitSamplersByString;
    private final Map<Integer, Sampler> possibilitySamplers;
    private final RateLimitingSampler.ResetHandler rlsResetHandler;

    public SamplerSpec(final ModuleManager moduleManager,
                       final LogAnalyzerModuleConfig moduleConfig) {
        super(moduleManager, moduleConfig);

        rateLimitSamplersByString = new ConcurrentHashMap<>();
        possibilitySamplers = new ConcurrentHashMap<>();
        rlsResetHandler = new RateLimitingSampler.ResetHandler();
    }

    public void rateLimit(final ExecutionContext ctx, final String id, final int rpm) {
        if (ctx.shouldAbort()) {
            return;
        }

        final Sampler sampler = rateLimitSamplersByString.computeIfAbsent(
            id, $ -> new RateLimitingSampler(rlsResetHandler).start());

        ((RateLimitingSampler) sampler).rpm(rpm);

        sampleWith(ctx, sampler);
    }

    private void sampleWith(final ExecutionContext ctx, final Sampler sampler) {
        if (ctx.shouldAbort()) {
            return;
        }
        if (sampler.sample()) {
            ctx.save();
        } else {
            ctx.drop();
        }
    }

}
