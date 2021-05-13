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
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import org.apache.skywalking.apm.agent.core.conf.Config;
import org.apache.skywalking.apm.agent.core.logging.core.converters.ThrowableConverter;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeDiagnosingMatcher;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.lang.reflect.Type;
import java.util.Map;

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

        ArgumentCaptor<String> argument = ArgumentCaptor.forClass(String.class);
        logger.debug("hello world");
        Mockito.verify(output, times(1)).write(argument.capture());
        Assert.assertThat(argument.getValue(), createMatcher("hello world", "DEBUG", null));
        logger.debug("hello {}", "world");
        Mockito.verify(output, times(2)).write(argument.capture());
        Assert.assertThat(argument.getValue(), createMatcher("hello world", "DEBUG", null));
        logger.info("hello world");
        Mockito.verify(output, times(3)).write(argument.capture());
        Assert.assertThat(argument.getValue(), createMatcher("hello world", "INFO", null));
        logger.info("hello {}", "world");
        Mockito.verify(output, times(4)).write(argument.capture());
        Assert.assertThat(argument.getValue(), createMatcher("hello world", "INFO", null));

        logger.warn("hello {}", "world");
        Mockito.verify(output, times(5)).write(argument.capture());
        Assert.assertThat(argument.getValue(), createMatcher("hello world", "WARN", null));
        logger.warn("hello world");
        Mockito.verify(output, times(6)).write(argument.capture());
        Assert.assertThat(argument.getValue(), createMatcher("hello world", "WARN", null));
        logger.error("hello world");
        Mockito.verify(output, times(7)).write(argument.capture());
        Assert.assertThat(argument.getValue(), createMatcher("hello world", "ERROR", null));
        Throwable t = new NullPointerException();
        logger.error("hello world", t);
        Mockito.verify(output, times(8)).write(argument.capture());
        Assert.assertThat(argument.getValue(), createMatcher("hello world", "ERROR", t));
        logger.error(t, "hello {}", "world");
        Mockito.verify(output, times(9)).write(argument.capture());
        Assert.assertThat(argument.getValue(), createMatcher("hello world", "ERROR", t));
    }

    private static class LogMatcher extends TypeSafeDiagnosingMatcher<String> {
        private static final Gson GSON = new Gson();
        private static final Type MAP_TYPE = new TypeToken<Map<String, String>>() {
        }.getType();

        private final String agent;
        private final String logger;
        private final String message;
        private final String level;
        private final Throwable t;

        public LogMatcher(String agent, String logger, String message, String level, Throwable t) {
            this.agent = agent;
            this.logger = logger;
            this.message = message;
            this.level = level;
            this.t = t;
        }

        @Override
        protected boolean matchesSafely(String item, Description mismatchDescription) {
            try {
                Map<String, String> logMap = GSON.fromJson(item, MAP_TYPE);
                if (!logMap.containsKey("agent_name")) {
                    mismatchDescription.appendText("agent_name did not exist");
                    return false;
                }
                if (!this.agent.equals(logMap.get("agent_name"))) {
                    mismatchDescription.appendText("agent_name was " + this.agent);
                    return false;
                }
                if (!logMap.containsKey("message")) {
                    mismatchDescription.appendText("agent_name did not exist");
                    return false;
                }
                if (!this.message.equals(logMap.get("message"))) {
                    mismatchDescription.appendText("message was " + this.message);
                    return false;
                }

                if (!logMap.containsKey("@timestamp")) {
                    mismatchDescription.appendText("@timestamp did not exist");
                    return false;
                }

                if (!logMap.containsKey("level")) {
                    mismatchDescription.appendText("level did not exist");
                    return false;
                }
                if (!this.level.equals(logMap.get("level"))) {
                    mismatchDescription.appendText("level was " + this.level);
                    return false;
                }

                if (!logMap.containsKey("logger")) {
                    mismatchDescription.appendText("logger did not exist");
                    return false;
                }
                if (!this.logger.equals(logMap.get("logger"))) {
                    mismatchDescription.appendText("logger was " + this.logger);
                    return false;
                }

                if (!logMap.containsKey("throwable")) {
                    mismatchDescription.appendText("throwable did not exist");
                    return false;
                }
                final String tStr = this.t == null ? "" : ThrowableConverter.format(this.t);
                if (!tStr.equals(logMap.get("throwable"))) {
                    mismatchDescription.appendText("throwable was " + tStr);
                    return false;
                }

            } catch (JsonSyntaxException ex) {
                mismatchDescription.appendText("not a valid json string");
                return false;
            }
            return true;
        }

        @Override
        public void describeTo(Description expectation) {
            expectation.appendText(
                    String.format("a valid json log format with {message=%s,level=%s,logger=%s,@timestamp=*,agent=%s}",
                            message, level, logger, agent));
        }
    }

    public static LogMatcher createMatcher(String message, String level, Throwable t) {
        return new LogMatcher("testAppFromConfig", JsonLoggerTest.class.getSimpleName(), message, level, t);
    }
}
