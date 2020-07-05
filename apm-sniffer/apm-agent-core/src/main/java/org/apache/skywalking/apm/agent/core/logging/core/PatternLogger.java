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
import org.apache.skywalking.apm.agent.core.logging.core.coverts.AgentNameConverter;
import org.apache.skywalking.apm.agent.core.logging.core.coverts.ClassConverter;
import org.apache.skywalking.apm.agent.core.logging.core.coverts.DateConverter;
import org.apache.skywalking.apm.agent.core.logging.core.coverts.LevelConverter;
import org.apache.skywalking.apm.agent.core.logging.core.coverts.MessageConverter;
import org.apache.skywalking.apm.agent.core.logging.core.coverts.ThreadConverter;
import org.apache.skywalking.apm.agent.core.logging.core.coverts.ThrowableConverter;
import org.apache.skywalking.apm.util.StringUtil;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

/**
 * A flexible Logger configurable with pattern string. This is default implementation of {@link ILog} This can parse a
 * pattern to the List of converter with Parser. We package LogEvent with message, level,timestamp ..., passing around
 * to the List of converter to concat actually Log-String.
 */
public class PatternLogger implements ILog {

    public static final Map<String, Class<? extends Converter>> DEFAULT_CONVERTER_MAP = new HashMap<String, Class<? extends Converter>>();

    static {
        DEFAULT_CONVERTER_MAP.put("thread", ThreadConverter.class);
        DEFAULT_CONVERTER_MAP.put("level", LevelConverter.class);
        DEFAULT_CONVERTER_MAP.put("agent_name", AgentNameConverter.class);
        DEFAULT_CONVERTER_MAP.put("timestamp", DateConverter.class);
        DEFAULT_CONVERTER_MAP.put("msg", MessageConverter.class);
        DEFAULT_CONVERTER_MAP.put("throwable", ThrowableConverter.class);
        DEFAULT_CONVERTER_MAP.put("class", ClassConverter.class);
    }

    public static final String DEFAULT_PATTERN = "%level %timestamp %thread %class : %msg %throwable";

    private String pattern;
    private List<Converter> converters;
    private String targetClass;

    public PatternLogger(Class targetClass, String pattern) {
        this(targetClass.getSimpleName(), pattern);
    }

    public PatternLogger(String targetClass, String pattern) {
        this.targetClass = targetClass;
        this.setPattern(pattern);
    }

    public String getPattern() {
        return pattern;
    }

    public void setPattern(String pattern) {
        if (StringUtil.isEmpty(pattern)) {
            pattern = DEFAULT_PATTERN;
        }
        this.pattern = pattern;
        converters = new Parser(pattern, DEFAULT_CONVERTER_MAP).parse();
    }

    protected void logger(LogLevel level, String message, Throwable e) {
        WriterFactory.getLogWriter().write(format(level, message, e));
    }

    private String replaceParam(String message, Object... parameters) {
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

    @Override
    public void info(String format) {
        if (isInfoEnable())
            logger(LogLevel.INFO, format, null);
    }

    @Override
    public void info(String format, Object... arguments) {
        if (isInfoEnable())
            logger(LogLevel.INFO, replaceParam(format, arguments), null);
    }

    @Override
    public void warn(String format, Object... arguments) {
        if (isWarnEnable())
            logger(LogLevel.WARN, replaceParam(format, arguments), null);
    }

    @Override
    public void warn(Throwable e, String format, Object... arguments) {
        if (isWarnEnable())
            logger(LogLevel.WARN, replaceParam(format, arguments), e);
    }

    @Override
    public void error(String format, Throwable e) {
        if (isErrorEnable())
            logger(LogLevel.ERROR, format, e);
    }

    @Override
    public void error(Throwable e, String format, Object... arguments) {
        if (isErrorEnable())
            logger(LogLevel.ERROR, replaceParam(format, arguments), e);
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
    public void debug(String format) {
        if (isDebugEnable()) {
            logger(LogLevel.DEBUG, format, null);
        }
    }

    @Override
    public void debug(String format, Object... arguments) {
        if (isDebugEnable()) {
            logger(LogLevel.DEBUG, replaceParam(format, arguments), null);
        }
    }

    @Override
    public void error(String format) {
        if (isErrorEnable()) {
            logger(LogLevel.ERROR, format, null);
        }
    }

    @Override
    public void debug(final Throwable t, final String format, final Object... arguments) {
        if (isDebugEnable()) {
            logger(LogLevel.DEBUG, replaceParam(format, arguments), t);
        }
    }

    String format(LogLevel level, String message, Throwable t) {
        LogEvent logEvent = new LogEvent(level, message, t, targetClass);
        StringBuilder stringBuilder = new StringBuilder();
        for (Converter converter : converters) {
            stringBuilder.append(converter.convert(logEvent));
        }
        return stringBuilder.toString();
    }
}
