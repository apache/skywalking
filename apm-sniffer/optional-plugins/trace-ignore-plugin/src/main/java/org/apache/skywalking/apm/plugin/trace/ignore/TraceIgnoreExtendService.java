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

import org.apache.skywalking.apm.agent.core.boot.AgentPackageNotFoundException;
import org.apache.skywalking.apm.agent.core.boot.OverrideImplementor;
import org.apache.skywalking.apm.agent.core.conf.ConfigNotFoundException;
import org.apache.skywalking.apm.agent.core.context.AbstractTracerContext;
import org.apache.skywalking.apm.agent.core.context.ContextManagerExtendService;
import org.apache.skywalking.apm.agent.core.context.IgnoredTracerContext;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.plugin.trace.ignore.conf.IgnoreConfig;
import org.apache.skywalking.apm.plugin.trace.ignore.conf.IgnoreConfigInitializer;
import org.apache.skywalking.apm.plugin.trace.ignore.matcher.FastPathMatcher;
import org.apache.skywalking.apm.plugin.trace.ignore.matcher.TracePathMatcher;
import org.apache.skywalking.apm.util.StringUtil;

/**
 * @author liujc [liujunc1993@163.com], kanro
 */
@OverrideImplementor(ContextManagerExtendService.class)
public class TraceIgnoreExtendService extends ContextManagerExtendService {

    private static final ILog LOGGER = LogManager.getLogger(TraceIgnoreExtendService.class);

    private static final String DEFAULT_PATH_SEPARATOR = "/";

    private static final String PATTERN_SEPARATOR = ",";

    private TracePathMatcher pathMatcher = new FastPathMatcher();

    private String[] patterns = new String[]{};

    @Override
    public void boot() {
        try {
            IgnoreConfigInitializer.initialize();
            if (StringUtil.isNotEmpty(IgnoreConfig.Trace.IGNORE_PATH)) {
                patterns = IgnoreConfig.Trace.IGNORE_PATH.split(PATTERN_SEPARATOR);
            }
        } catch (ConfigNotFoundException | AgentPackageNotFoundException e) {
            LOGGER.error("trace ignore config init error", e);
        }
    }

    @Override
    public AbstractTracerContext createTraceContext(String operationName, boolean forceSampling) {
        if (patterns.length > 0 && !forceSampling) {
            for (String pattern : patterns) {
                if (pathMatcher.match(pattern, operationName)) {
                    LOGGER.debug("operationName : " + operationName + " Ignore tracking");
                    return new IgnoredTracerContext();
                }
            }
        }
        return super.createTraceContext(operationName, forceSampling);
    }
}
