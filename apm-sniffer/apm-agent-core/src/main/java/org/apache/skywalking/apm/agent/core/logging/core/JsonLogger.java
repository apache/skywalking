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

import com.google.gson.Gson;
import org.apache.skywalking.apm.agent.core.logging.core.converters.LiteralConverter;

import java.util.HashMap;
import java.util.Map;

public class JsonLogger extends AbstractLogger {
    private final Gson gson;

    public JsonLogger(Class<?> targetClass, Gson gson) {
        this(targetClass.getSimpleName(), gson);
    }

    public JsonLogger(String targetClass, Gson gson) {
        super(targetClass);
        this.gson = gson;
        for (Map.Entry<String, Class<? extends Converter>> entry : DEFAULT_CONVERTER_MAP.entrySet()) {
            try {
                if (converters instanceof LiteralConverter) {
                    continue;
                }
                converters.add(entry.getValue().newInstance());
            } catch (IllegalAccessException | InstantiationException ignore) {
            }
        }
    }

    protected void logger(LogLevel level, String message, Throwable e) {
        WriterFactory.getLogWriter().write(generateJson(level, message, e));
    }

    String generateJson(LogLevel level, String message, Throwable e) {
        LogEvent logEvent = new LogEvent(level, message, e, this.targetClass);
        Map<String, String> log = new HashMap<>();
        for (Converter converter : this.converters) {
            log.put(converter.getKey(), converter.convert(logEvent));
        }
        return this.gson.toJson(log);
    }
}
