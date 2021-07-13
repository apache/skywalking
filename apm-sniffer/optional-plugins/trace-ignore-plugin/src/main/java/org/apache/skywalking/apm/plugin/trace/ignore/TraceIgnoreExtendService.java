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

package org.apache.skywalking.apm.plugin.trace.ignore;

import org.apache.skywalking.apm.agent.core.boot.OverrideImplementor;
import org.apache.skywalking.apm.agent.core.boot.ServiceManager;
import org.apache.skywalking.apm.agent.core.conf.dynamic.ConfigurationDiscoveryService;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.agent.core.sampling.SamplingService;
import org.apache.skywalking.apm.plugin.trace.ignore.conf.IgnoreConfig;
import org.apache.skywalking.apm.plugin.trace.ignore.conf.IgnoreConfigInitializer;
import org.apache.skywalking.apm.plugin.trace.ignore.matcher.FastPathMatcher;
import org.apache.skywalking.apm.plugin.trace.ignore.matcher.TracePathMatcher;
import org.apache.skywalking.apm.util.StringUtil;

@OverrideImplementor(SamplingService.class)
public class TraceIgnoreExtendService extends SamplingService {
    private static final ILog LOGGER = LogManager.getLogger(TraceIgnoreExtendService.class);
    private static final String PATTERN_SEPARATOR = ",";
    private TracePathMatcher pathMatcher = new FastPathMatcher();
    private volatile String[] patterns = new String[] {};
    private TraceIgnorePatternWatcher traceIgnorePatternWatcher;

    @Override
    public void prepare() {
        super.prepare();
    }

    @Override
    public void boot() {
        super.boot();

        IgnoreConfigInitializer.initialize();
        if (StringUtil.isNotEmpty(IgnoreConfig.Trace.IGNORE_PATH)) {
            patterns = IgnoreConfig.Trace.IGNORE_PATH.split(PATTERN_SEPARATOR);
        }

        traceIgnorePatternWatcher = new TraceIgnorePatternWatcher("agent.trace.ignore_path", this);
        ServiceManager.INSTANCE.findService(ConfigurationDiscoveryService.class)
                               .registerAgentConfigChangeWatcher(traceIgnorePatternWatcher);

        handleTraceIgnorePatternsChanged();
    }

    @Override
    public void onComplete() {
    }

    @Override
    public void shutdown() {
        super.shutdown();
    }

    @Override
    public boolean trySampling(final String operationName) {
        if (patterns.length > 0) {
            for (String pattern : patterns) {
                if (pathMatcher.match(pattern, operationName)) {
                    LOGGER.debug("operationName : " + operationName + " Ignore tracking");
                    return false;
                }
            }
        }
        return super.trySampling(operationName);
    }

    @Override
    public void forceSampled() {
        super.forceSampled();
    }

    void handleTraceIgnorePatternsChanged() {
        if (StringUtil.isNotBlank(traceIgnorePatternWatcher.getTraceIgnorePathPatterns())) {
            patterns = traceIgnorePatternWatcher.getTraceIgnorePathPatterns().split(PATTERN_SEPARATOR);
        } else {
            patterns = new String[] {};
        }
    }
}
