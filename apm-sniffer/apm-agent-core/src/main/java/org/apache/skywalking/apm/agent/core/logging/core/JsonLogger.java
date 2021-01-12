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

/**
 * An alternative logger for the SkyWalking agent. The default layout is
 * {
 *     "@timestamp": "", // timestamp
 *     "logger": "", // name of the Logger
 *     "level": "", // info|debug|warn|error
 *     "thread": "", // thread where the log method is called
 *     "message": "", // your log message
 *     "throwable": "",
 *     "agent_name" "service_name"
 * }
 */
public class JsonLogger extends AbstractLogger {
    private final Gson gson;

    public JsonLogger(Class<?> targetClass, Gson gson) {
        this(targetClass.getSimpleName(), gson);
    }

    /**
     * In the Constructor, the instances of converters are created,
     * except those {@link LiteralConverter} since this class is used
     * only the literals in {@link PatternLogger} ,
     * and thus should not be added to the json log.
     *
     * @param targetClass the logger class
     * @param gson instance of Gson works as json serializer
     */
    public JsonLogger(String targetClass, Gson gson) {
        super(targetClass);
        this.gson = gson;
        for (Map.Entry<String, Class<? extends Converter>> entry : DEFAULT_CONVERTER_MAP.entrySet()) {
            final Class<? extends Converter> converterClass = entry.getValue();
            try {
                if (converters instanceof LiteralConverter) {
                    continue;
                }
                converters.add(converterClass.newInstance());
            } catch (IllegalAccessException | InstantiationException e) {
                throw new IllegalStateException("Create Converter error. Class: " + converterClass, e);
            }
        }
    }

    @Override
    protected String format(LogLevel level, String message, Throwable e) {
        LogEvent logEvent = new LogEvent(level, message, e, this.targetClass);
        Map<String, String> log = new HashMap<>();
        for (Converter converter : this.converters) {
            log.put(converter.getKey(), converter.convert(logEvent));
        }
        return this.gson.toJson(log);
    }
}
