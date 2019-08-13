/*
 * Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package org.apache.skywalking.apm.agent.core.logging.core;

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

/**
 * @author alvin
 */
public class PatternLogger extends EasyLogger {

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

    public PatternLogger(Class targetClass, String pattern) {
        this(targetClass.getSimpleName(), pattern);
    }

    public PatternLogger(String targetClass, String pattern) {
        super(targetClass);
        this.setPattern(pattern);
    }

    public String getPattern() {
        return pattern;
    }

    public void setPattern(String pattern) {
        if (StringUtil.isEmpty(pattern)) {
            pattern = DEFAULT_PATTERN;
        }
        converters = new Parser(pattern, DEFAULT_CONVERTER_MAP).parse();
    }

    @Override
    String format(LogLevel level, String message, Throwable t) {
        LogEvent logEvent = new LogEvent(level, message, t, targetClass);
        StringBuilder stringBuilder = new StringBuilder();
        for (Converter converter : converters) {
            stringBuilder.append(converter.convert(logEvent));
        }
        return stringBuilder.toString();
    }
}
