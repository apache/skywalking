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

import org.apache.skywalking.apm.agent.core.logging.core.coverts.AgentNameConverter;
import org.apache.skywalking.apm.agent.core.logging.core.coverts.ClassConverter;
import org.apache.skywalking.apm.agent.core.logging.core.coverts.DateConverter;
import org.apache.skywalking.apm.agent.core.logging.core.coverts.LevelConverter;
import org.apache.skywalking.apm.agent.core.logging.core.coverts.MessageConverter;
import org.apache.skywalking.apm.agent.core.logging.core.coverts.ThreadConverter;
import org.apache.skywalking.apm.agent.core.logging.core.coverts.ThrowableConverter;

import java.util.HashMap;
import java.util.Map;

/**
 * @author alvin
 */
public class ConverterMapHolder {
    private static Map<String, Class<? extends Converter>> converterMap = new HashMap<String, Class<? extends Converter>>();

    static {
        converterMap.put("thread", ThreadConverter.class);
        converterMap.put("level", LevelConverter.class);
        converterMap.put("agent_name", AgentNameConverter.class);
        converterMap.put("timestamp", DateConverter.class);
        converterMap.put("msg", MessageConverter.class);
        converterMap.put("throwable", ThrowableConverter.class);
        converterMap.put("class", ClassConverter.class);
    }

    public static Map<String, Class<? extends Converter>> getConverterMap() {
        return converterMap;
    }
}
