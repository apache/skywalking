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

import org.apache.skywalking.apm.agent.core.conf.Config;
import org.apache.skywalking.apm.util.PropertyPlaceholderHelper;
import org.apache.skywalking.apm.util.StringUtil;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;

/**
 * @author alvin
 */
public class PatternLogger extends EasyLogger {

    public static final String DEFAULT_PATTERN = "%{level} %{timestamp} %{thread} %{class} : %{msg} %{throwable}";

    private String pattern;

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
        this.pattern = pattern;
    }

    @Override
    String format(LogLevel level, String message, Throwable t) {
        Properties props = buildContext(level, message, t);
        return PropertyPlaceholderHelper.LOGGER.replacePlaceholders(getPattern(), props);
    }

    private Properties buildContext(LogLevel level, String message, Throwable t) {
        Properties props = new Properties();
        props.putAll(System.getenv());
        props.putAll(System.getProperties());
        props.put("agent.service_name", Config.Agent.SERVICE_NAME);
        props.put("agent.namespace", Config.Agent.NAMESPACE);
        props.put("level", level.name());
        props.put("timestamp", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss:SSS").format(new Date()));
        props.put("thread", Thread.currentThread().getName());
        props.put("class", targetClass);
        props.put("msg", message);
        props.put("throwable", t == null ? "" : format(t));
        return props;
    }
}
