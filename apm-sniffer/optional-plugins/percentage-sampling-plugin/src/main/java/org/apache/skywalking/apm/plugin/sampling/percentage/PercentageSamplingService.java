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

package org.apache.skywalking.apm.plugin.sampling.percentage;

import java.util.concurrent.ThreadLocalRandom;
import org.apache.skywalking.apm.agent.core.boot.OverrideImplementor;
import org.apache.skywalking.apm.agent.core.context.trace.TraceSegment;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.agent.core.sampling.SamplingService;

/**
 * The <code>PercentageSamplingService</code> take charge of how to sample the {@link TraceSegment}. This has the same
 * functionality as core SamplingService but in a percentage way.
 * <p>
 */
@OverrideImplementor(SamplingService.class)
public class PercentageSamplingService extends SamplingService {
    private static final ILog LOGGER = LogManager.getLogger(PercentageSamplingService.class);
    private static final int MAX_N = 10000;
    private volatile boolean on = false;
    public static int DFT_SAMPLING_RATE = 0;

    @Override
    public void prepare() {
    }

    @Override
    public void boot() {
        LOGGER.info("percentage sampling service booted");
        if (PercentageSamplingPluginConfig.Plugin.Sampling.SAMPLE_RATE >= 0) {
            DFT_SAMPLING_RATE = PercentageSamplingPluginConfig.Plugin.Sampling.SAMPLE_RATE;
            LOGGER.info("enabling percentage sampling with default sampling rate " + DFT_SAMPLING_RATE);
            on = true;
        }
    }

    @Override
    public void onComplete() {

    }

    @Override
    public void shutdown() {
    }

    /**
     * @param operationName The first operation name of the new tracing context.
     * @return true, if sampling mechanism is on
     */
    @Override
    public boolean trySampling(String operationName) {
        if (on) {
            return ThreadLocalRandom.current().nextInt(0, MAX_N) < DFT_SAMPLING_RATE;
        }
        return true;
    }

    /**
     * Increase the sampling factor by force, to avoid sampling too many traces. If many distributed traces require
     * sampled, the trace beginning at local, has less chance to be sampled.
     */
    @Override
    public void forceSampled() {
    }
}

