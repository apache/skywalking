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

package org.apache.skywalking.apm.agent.core.logging.core;

import org.apache.skywalking.apm.agent.core.conf.Config;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.core.converters.AgentNameConverter;
import org.apache.skywalking.apm.agent.core.logging.core.converters.ClassConverter;
import org.apache.skywalking.apm.agent.core.logging.core.converters.DateConverter;
import org.apache.skywalking.apm.agent.core.logging.core.converters.LevelConverter;
import org.apache.skywalking.apm.agent.core.logging.core.converters.MessageConverter;
import org.apache.skywalking.apm.agent.core.logging.core.converters.ThreadConverter;
import org.apache.skywalking.apm.agent.core.logging.core.converters.ThrowableConverter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

/**
 * An abstract class to simplify the real implementation of the loggers.
 * It hold the class name of the logger, and is responsible for log level check,
 * message interpolation, etc.
 */
public abstract class AbstractLogger implements ILog {
    public static final Map<String, Class<? extends Converter>> DEFAULT_CONVERTER_MAP = new HashMap<>();
    protected List<Converter> converters = new ArrayList<>();

    static {
        DEFAULT_CONVERTER_MAP.put("thread", ThreadConverter.class);
        DEFAULT_CONVERTER_MAP.put("level", LevelConverter.class);
        DEFAULT_CONVERTER_MAP.put("agent_name", AgentNameConverter.class);
        DEFAULT_CONVERTER_MAP.put("timestamp", DateConverter.class);
        DEFAULT_CONVERTER_MAP.put("msg", MessageConverter.class);
        DEFAULT_CONVERTER_MAP.put("throwable", ThrowableConverter.class);
        DEFAULT_CONVERTER_MAP.put("class", ClassConverter.class);
    }

    protected final String targetClass;

    public AbstractLogger(String targetClass) {
        this.targetClass = targetClass;
    }

    @Override
    public void info(String message) {
        if (this.isInfoEnable()) {
            this.logger(LogLevel.INFO, message, null);
        }
    }

    @Override
    public void info(String message, Object... objects) {
        if (this.isInfoEnable()) {
            this.logger(LogLevel.INFO, replaceParam(message, objects), null);
        }
    }

    @Override
    public void info(final Throwable throwable, final String message, final Object... objects) {
        if (this.isInfoEnable()) {
            this.logger(LogLevel.INFO, replaceParam(message, objects), throwable);
        }
    }

    @Override
    public void warn(String message, Object... objects) {
        if (this.isWarnEnable()) {
            this.logger(LogLevel.WARN, replaceParam(message, objects), null);
        }
    }

    @Override
    public void warn(Throwable throwable, String message, Object... objects) {
        if (this.isWarnEnable()) {
            this.logger(LogLevel.WARN, replaceParam(message, objects), throwable);
        }
    }

    @Override
    public void error(String message, Throwable throwable) {
        if (this.isErrorEnable()) {
            this.logger(LogLevel.ERROR, message, throwable);
        }
    }

    @Override
    public void error(Throwable throwable, String message, Object... objects) {
        if (this.isErrorEnable()) {
            this.logger(LogLevel.ERROR, replaceParam(message, objects), throwable);
        }
    }

    @Override
    public void error(String message) {
        if (this.isErrorEnable()) {
            this.logger(LogLevel.ERROR, message, null);
        }
    }

    @Override
    public void debug(String message) {
        if (this.isDebugEnable()) {
            this.logger(LogLevel.DEBUG, message, null);
        }
    }

    @Override
    public void debug(String message, Object... objects) {
        if (this.isDebugEnable()) {
            this.logger(LogLevel.DEBUG, replaceParam(message, objects), null);
        }
    }

    @Override
    public void debug(Throwable throwable, String message, Object... objects) {
        if (this.isDebugEnable()) {
            this.logger(LogLevel.DEBUG, replaceParam(message, objects), throwable);
        }
    }

    @Override
    public boolean isDebugEnable() {
        return LogLevel.DEBUG.compareTo(Config.Logging.LEVEL) >= 0;
    }

    @Override
    public boolean isInfoEnable() {
        return LogLevel.INFO.compareTo(Config.Logging.LEVEL) >= 0;
    }

    @Override
    public boolean isWarnEnable() {
        return LogLevel.WARN.compareTo(Config.Logging.LEVEL) >= 0;
    }

    @Override
    public boolean isErrorEnable() {
        return LogLevel.ERROR.compareTo(Config.Logging.LEVEL) >= 0;
    }

    @Override
    public boolean isTraceEnabled() {
        return LogLevel.TRACE.compareTo(Config.Logging.LEVEL) >= 0;
    }

    @Override
    public void trace(final String message) {
        if (this.isTraceEnabled()) {
            this.logger(LogLevel.TRACE, message, null);
        }
    }

    @Override
    public void trace(final String message, final Object... objects) {
        if (this.isTraceEnabled()) {
            this.logger(LogLevel.TRACE, replaceParam(message, objects), null);
        }
    }

    @Override
    public void trace(final Throwable throwable, final String message, final Object... objects) {
        if (this.isTraceEnabled()) {
            this.logger(LogLevel.TRACE, replaceParam(message, objects), throwable);
        }
    }

    protected String replaceParam(String message, Object... parameters) {
        if (message == null) {
            return message;
        }
        int startSize = 0;
        int parametersIndex = 0;
        int index;
        String tmpMessage = message;
        while ((index = message.indexOf("{}", startSize)) != -1) {
            if (parametersIndex >= parameters.length) {
                break;
            }
            /**
             * @Fix the Illegal group reference issue
             */
            tmpMessage = tmpMessage.replaceFirst("\\{\\}", Matcher.quoteReplacement(String.valueOf(parameters[parametersIndex++])));
            startSize = index + 2;
        }
        return tmpMessage;
    }

    protected void logger(LogLevel level, String message, Throwable e) {
        WriterFactory.getLogWriter().write(this.format(level, message, e));
    }

    /**
     * The abstract method left for real loggers.
     * Any implementation MUST return string, which will be directly transferred to log destination,
     * i.e. log files OR stdout
     *
     * @param level log level
     * @param message log message, which has been interpolated with user-defined parameters.
     * @param e throwable if exists
     * @return string representation of the log, for example, raw json string for {@link JsonLogger}
     */
    protected abstract String format(LogLevel level, String message, Throwable e);

}
