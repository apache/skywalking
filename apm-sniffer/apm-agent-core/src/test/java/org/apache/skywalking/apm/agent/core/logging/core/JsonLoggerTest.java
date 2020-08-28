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
import com.google.gson.reflect.TypeToken;
import org.apache.skywalking.apm.agent.core.conf.Config;
import org.apache.skywalking.apm.agent.core.logging.core.converters.ThrowableConverter;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.lang.reflect.Type;
import java.util.Map;

import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.times;

public class JsonLoggerTest {
    @BeforeClass
    public static void initAndHoldOut() {
        Config.Agent.SERVICE_NAME = "testAppFromConfig";
    }

    @Test
    public void testLog() {
        final IWriter output = Mockito.mock(IWriter.class);
        JsonLogger logger = new JsonLogger(JsonLoggerTest.class, new Gson()) {
            @Override
            protected void logger(LogLevel level, String message, Throwable e) {
                output.write(format(level, message, e));
            }
        };

        Assert.assertTrue(logger.isDebugEnable());
        Assert.assertTrue(logger.isInfoEnable());
        Assert.assertTrue(logger.isWarnEnable());
        Assert.assertTrue(logger.isErrorEnable());

        logger.debug("hello world");
        logger.debug("hello {}", "world");
        logger.info("hello world");
        logger.info("hello {}", "world");

        logger.warn("hello {}", "world");
        logger.warn("hello world");
        logger.error("hello world");
        logger.error("hello world", new NullPointerException());
        logger.error(new NullPointerException(), "hello {}", "world");

        Mockito.verify(output, times(9)).write(anyString());
    }

    @Test
    public void testJsonLogKeys() {
        final IWriter output = Mockito.mock(IWriter.class);
        ArgumentCaptor<String> argument = ArgumentCaptor.forClass(String.class);
        JsonLogger logger = new JsonLogger(JsonLoggerTest.class, new Gson()) {
            @Override
            protected void logger(LogLevel level, String message, Throwable e) {
                output.write(format(level, message, e));
            }
        };
        logger.info("hello world");
        Mockito.verify(output).write(argument.capture());
        Gson gson = new Gson();
        Type type = new TypeToken<Map<String, String>>() {
        }.getType();
        Map<String, String> logMap = gson.fromJson(argument.getValue(), type);
        Assert.assertTrue(logMap.containsKey("@timestamp"));
        Assert.assertTrue(logMap.containsKey("message"));
        Assert.assertEquals(logMap.get("message"), "hello world");
        Assert.assertTrue(logMap.containsKey("thread"));
        Assert.assertTrue(logMap.containsKey("level"));
        Assert.assertTrue(logMap.containsKey("agent_name"));
        Assert.assertTrue(logMap.containsKey("throwable"));
        Assert.assertEquals(logMap.get("throwable"), "");
        Assert.assertTrue(logMap.containsKey("logger"));
        Assert.assertEquals(logMap.get("logger"), "JsonLoggerTest");
    }

    @Test
    public void testExceptionLogging() {
        final IWriter output = Mockito.mock(IWriter.class);
        ArgumentCaptor<String> argument = ArgumentCaptor.forClass(String.class);
        JsonLogger logger = new JsonLogger(JsonLoggerTest.class, new Gson()) {
            @Override
            protected void logger(LogLevel level, String message, Throwable e) {
                output.write(format(level, message, e));
            }
        };
        Exception e = new IllegalArgumentException();
        String throwableStr = ThrowableConverter.format(e);
        logger.error(e, "int cannot be negative");
        Mockito.verify(output).write(argument.capture());
        Gson gson = new Gson();
        Type type = new TypeToken<Map<String, String>>() {
        }.getType();
        Map<String, String> logMap = gson.fromJson(argument.getValue(), type);
        Assert.assertTrue(logMap.containsKey("throwable"));
        Assert.assertEquals(logMap.get("throwable"), throwableStr);
    }
}
