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

import com.google.common.collect.Lists;
import org.apache.skywalking.apm.agent.core.conf.Config;
import org.apache.skywalking.apm.agent.core.conf.Constants;
import org.hamcrest.core.StringContains;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.List;

import static org.mockito.Mockito.times;

public class PatternLoggerTest {

    public static final String PATTERN = "%timestamp+0800 %level [%agent_name,,,] [%thread] %class:-1 %msg %throwable";

    @BeforeClass
    public static void initAndHoldOut() {
        Config.Agent.SERVICE_NAME = "testAppFromConfig";
    }

    @Test
    public void testLog() {
        final IWriter output = Mockito.mock(IWriter.class);
        PatternLogger logger = new PatternLogger(PatternLoggerTest.class, PATTERN) {
            @Override
            protected void logger(LogLevel level, String message, Throwable e) {
                String r = format(level, message, e);
                output.write(r);
            }
        };

        Assert.assertTrue(logger.isDebugEnable());
        Assert.assertTrue(logger.isInfoEnable());
        Assert.assertTrue(logger.isWarnEnable());
        Assert.assertTrue(logger.isErrorEnable());

        ArgumentCaptor<String> argument = ArgumentCaptor.forClass(String.class);
        logger.debug("hello world");
        Mockito.verify(output, times(1)).write(argument.capture());
        Assert.assertThat(argument.getValue(), StringContains.containsString("DEBUG [testAppFromConfig,,,] [main] PatternLoggerTest:-1 hello world"));
        logger.debug("hello {}", "world");
        Mockito.verify(output, times(2)).write(argument.capture());
        Assert.assertThat(argument.getValue(), StringContains.containsString("DEBUG [testAppFromConfig,,,] [main] PatternLoggerTest:-1 hello world"));

        logger.info("hello world");
        Mockito.verify(output, times(3)).write(argument.capture());
        Assert.assertThat(argument.getValue(), StringContains.containsString("INFO [testAppFromConfig,,,] [main] PatternLoggerTest:-1 hello world"));
        logger.info("hello {}", "world");
        Mockito.verify(output, times(4)).write(argument.capture());
        Assert.assertThat(argument.getValue(), StringContains.containsString("INFO [testAppFromConfig,,,] [main] PatternLoggerTest:-1 hello world"));

        logger.warn("hello {}", "world");
        Mockito.verify(output, times(5)).write(argument.capture());
        Assert.assertThat(argument.getValue(), StringContains.containsString("WARN [testAppFromConfig,,,] [main] PatternLoggerTest:-1 hello world"));
        logger.warn("hello world");
        Mockito.verify(output, times(6)).write(argument.capture());
        Assert.assertThat(argument.getValue(), StringContains.containsString("WARN [testAppFromConfig,,,] [main] PatternLoggerTest:-1 hello world"));

        logger.error("hello world");
        Mockito.verify(output, times(7)).write(argument.capture());
        Assert.assertThat(argument.getValue(), StringContains.containsString("ERROR [testAppFromConfig,,,] [main] PatternLoggerTest:-1 hello world"));
        logger.error("hello world", new NullPointerException());
        Mockito.verify(output, times(8)).write(argument.capture());
        Assert.assertThat(argument.getValue(), StringContains.containsString("ERROR [testAppFromConfig,,,] [main] PatternLoggerTest:-1 hello world"));
        Assert.assertThat(argument.getValue(), StringContains.containsString("java.lang.NullPointerException"));
        logger.error(new NullPointerException(), "hello {}", "world");
        Mockito.verify(output, times(9)).write(argument.capture());
        Assert.assertThat(argument.getValue(), StringContains.containsString("java.lang.NullPointerException"));
    }

    @Test
    public void testLogWithSpecialChar() {
        final IWriter output = Mockito.mock(IWriter.class);
        PatternLogger logger = new PatternLogger(PatternLoggerTest.class, PATTERN) {
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
        logger.debug("$^!@#*()");
        Mockito.verify(output, times(1)).write(argument.capture());
        Assert.assertThat(argument.getValue(), StringContains.containsString("DEBUG [testAppFromConfig,,,] [main] PatternLoggerTest:-1 $^!@#*()"));
        logger.debug("hello {}", "!@#$%^&*(),./[]:;");
        Mockito.verify(output, times(2)).write(argument.capture());
        Assert.assertThat(argument.getValue(), StringContains.containsString("DEBUG [testAppFromConfig,,,] [main] PatternLoggerTest:-1 hello !@#$%^&*(),./[]:;"));

        logger.info("{}{}");
        Mockito.verify(output, times(3)).write(argument.capture());
        Assert.assertThat(argument.getValue(), StringContains.containsString("INFO [testAppFromConfig,,,] [main] PatternLoggerTest:-1 {}{}"));
        logger.info("hello {}", "{}{}");
        Mockito.verify(output, times(4)).write(argument.capture());
        Assert.assertThat(argument.getValue(), StringContains.containsString("INFO [testAppFromConfig,,,] [main] PatternLoggerTest:-1 hello {}{}"));

        logger.warn("hello {}", "\\");
        Mockito.verify(output, times(5)).write(argument.capture());
        Assert.assertThat(argument.getValue(), StringContains.containsString("WARN [testAppFromConfig,,,] [main] PatternLoggerTest:-1 hello \\"));
        logger.warn("hello \\");
        Mockito.verify(output, times(6)).write(argument.capture());
        Assert.assertThat(argument.getValue(), StringContains.containsString("WARN [testAppFromConfig,,,] [main] PatternLoggerTest:-1 hello \\"));

        logger.error("hello <>..");
        Mockito.verify(output, times(7)).write(argument.capture());
        Assert.assertThat(argument.getValue(), StringContains.containsString("ERROR [testAppFromConfig,,,] [main] PatternLoggerTest:-1 hello <>.."));
        logger.error("hello ///\\\\", new NullPointerException());
        Mockito.verify(output, times(8)).write(argument.capture());
        Assert.assertThat(argument.getValue(), StringContains.containsString("ERROR [testAppFromConfig,,,] [main] PatternLoggerTest:-1 hello ///\\\\"));
        logger.error(new NullPointerException(), "hello {}", "&&&**%%");
        Mockito.verify(output, times(9)).write(argument.capture());
        Assert.assertThat(argument.getValue(), StringContains.containsString("ERROR [testAppFromConfig,,,] [main] PatternLoggerTest:-1 hello &&&**%%"));
    }

    @Test
    public void testLogOk_whenPatternHasKeyword() {
        final List<String> strings = Lists.newArrayList();
        PatternLogger logger = new PatternLogger(PatternLoggerTest.class, "logmsg: %%%%%%!@#$\\%^&*() %{this is message} \\\\ \\n\\t \t\n %%msg") {
            @Override
            protected void logger(LogLevel level, String message, Throwable e) {
                String r = format(level, message, e);
                strings.add(r);
            }
        };
        logger.info("msg");
        Assert.assertThat(strings.get(0), StringContains.containsString("logmsg: %%%%%%!@#$%^&*() %{this is message} \\ \n\t \t\n %msg"));
    }

    @Test
    public void testLogFormat() {
        final List<String> strings = Lists.newArrayList();
        PatternLogger logger = new PatternLogger(PatternLoggerTest.class, PATTERN) {
            @Override
            protected void logger(LogLevel level, String message, Throwable e) {
                String r = format(level, message, e);
                strings.add(r);
            }
        };
        NullPointerException exception = new NullPointerException();
        logger.error("hello world", exception);
        logger.error("hello world", null);
        String formatLines = strings.get(0);
        String[] lines = formatLines.split(Constants.LINE_SEPARATOR);
        Assert.assertThat(lines[0], StringContains.containsString("ERROR [testAppFromConfig,,,] [main] PatternLoggerTest:-1 hello world "));
        Assert.assertEquals("java.lang.NullPointerException", lines[1]);
        Assert.assertThat(lines[2], StringContains.containsString("PatternLoggerTest.testLogFormat"));
        Assert.assertEquals(strings.get(1).split(Constants.LINE_SEPARATOR).length, 1);
    }

}
