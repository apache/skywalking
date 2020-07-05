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

package org.apache.skywalking.apm.agent.core.plugin.bootstrap;

import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;

/**
 * The log bridge makes the ILog accessible inside bootstrap classloader, especially for internal interceptor.
 */
public class BootstrapPluginLogBridge implements IBootstrapLog {
    public static IBootstrapLog getLogger(String clazz) {
        return new BootstrapPluginLogBridge(clazz);
    }

    private final ILog logger;

    private BootstrapPluginLogBridge(String clazz) {
        logger = LogManager.getLogger(clazz);
    }

    @Override
    public void info(String format) {
        logger.info(format);
    }

    @Override
    public void info(String format, Object... arguments) {
        logger.info(format, arguments);
    }

    @Override
    public void warn(String format, Object... arguments) {
        logger.warn(format, arguments);
    }

    @Override
    public void warn(Throwable e, String format, Object... arguments) {
        logger.warn(e, format, arguments);
    }

    @Override
    public void error(String format, Throwable e) {
        logger.error(format, e);
    }

    @Override
    public void error(Throwable e, String format, Object... arguments) {
        logger.error(e, format, arguments);
    }

    @Override
    public boolean isDebugEnable() {
        return logger.isDebugEnable();
    }

    @Override
    public boolean isInfoEnable() {
        return logger.isInfoEnable();
    }

    @Override
    public boolean isWarnEnable() {
        return logger.isWarnEnable();
    }

    @Override
    public boolean isErrorEnable() {
        return logger.isErrorEnable();
    }

    @Override
    public void debug(String format) {
        logger.debug(format);
    }

    @Override
    public void debug(String format, Object... arguments) {
        logger.debug(format, arguments);
    }

    @Override
    public void error(String format) {
        logger.error(format);
    }
}
