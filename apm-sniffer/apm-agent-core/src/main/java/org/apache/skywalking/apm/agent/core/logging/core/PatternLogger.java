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

import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.util.StringUtil;

/**
 * A flexible Logger configurable with pattern string. This is default implementation of {@link ILog} This can parse a
 * pattern to the List of converter with Parser. We package LogEvent with message, level,timestamp ..., passing around
 * to the List of converter to concat actually Log-String.
 */
public class PatternLogger extends AbstractLogger {
    public static final String DEFAULT_PATTERN = "%level %timestamp %thread %class : %msg %throwable";

    private String pattern;

    public PatternLogger(Class<?> targetClass, String pattern) {
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
        this.pattern = pattern;
        this.converters = new Parser(pattern, DEFAULT_CONVERTER_MAP).parse();
    }

    @Override
    protected String format(LogLevel level, String message, Throwable t) {
        LogEvent logEvent = new LogEvent(level, message, t, targetClass);
        StringBuilder stringBuilder = new StringBuilder();
        for (Converter converter : this.converters) {
            stringBuilder.append(converter.convert(logEvent));
        }
        return stringBuilder.toString();
    }
}
