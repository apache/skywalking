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

package org.apache.skywalking.oap.server.ai.evaluation;

import org.apache.skywalking.oap.server.core.source.TALFunctionProvider;
import org.apache.skywalking.oap.server.library.module.ModuleManager;

/**
 * The evaluation's sampling settings, for the rules that sample GenAI spans for evaluation, {@code native-ai-evaluation}
 * and {@code otlp-ai-evaluation}: the policy is written in the rule, from these, where the listeners asked
 * {@code IAIEvaluationService.shouldSample}.
 */
public class AIEvaluationTALFunctions implements TALFunctionProvider {
    private AIEvaluationSamplingConfig sampling;

    @Override
    public String module() {
        return AIEvaluationModule.NAME;
    }

    /**
     * @return true: the sampling settings do not depend on the input
     */
    @Override
    public boolean supportsInputType(final String inputType) {
        return true;
    }

    @Override
    public void bind(final ModuleManager moduleManager) {
        if (moduleManager == null) {
            // The default registry a unit test compiles against, which starts no module; the functions are not run.
            return;
        }
        sampling = moduleManager.find(AIEvaluationModule.NAME).provider().getService(AIEvaluationSamplingConfig.class);
    }

    /**
     * @return whether the module evaluates, see {@link AIEvaluationSamplingConfig#isActive()}
     */
    public boolean evaluationActive() {
        return sampling.isActive();
    }

    /**
     * @return the traces sampled out of 1,000,000, see {@link AIEvaluationSamplingConfig#getSampleRate()}
     */
    public int evaluationSampleRate() {
        return sampling.getSampleRate();
    }
}
