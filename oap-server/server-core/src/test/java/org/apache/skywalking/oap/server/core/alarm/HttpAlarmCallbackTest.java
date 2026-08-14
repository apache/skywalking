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

package org.apache.skywalking.oap.server.core.alarm;

import com.sun.net.httpserver.HttpServer;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HttpAlarmCallbackTest {

    private static HttpServer SERVER;
    private static CapturingAppender APPENDER;

    /**
     * {@link HttpAlarmCallback#post} reports delivery only through the log, so the status handling can only be
     * observed by capturing what it logs.
     */
    private static class CapturingAppender extends AbstractAppender {
        private final List<LogEvent> events = new CopyOnWriteArrayList<>();

        CapturingAppender() {
            super("capturing", null, null, true, Property.EMPTY_ARRAY);
        }

        @Override
        public void append(LogEvent event) {
            events.add(event.toImmutable());
        }
    }

    private static final class Callback extends HttpAlarmCallback {
        @Override
        protected void doAlarmCallback(List<AlarmMessage> alarmMessages, boolean isRecovery) {
        }

        String send(int status) throws IOException, InterruptedException {
            return post(
                URI.create("http://127.0.0.1:" + SERVER.getAddress().getPort() + "/status/" + status),
                "{}", Map.of()
            );
        }
    }

    @BeforeAll
    public static void setUp() throws IOException {
        SERVER = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        SERVER.createContext("/status", exchange -> {
            final String path = exchange.getRequestURI().getPath();
            final int status = Integer.parseInt(path.substring(path.lastIndexOf('/') + 1));
            final byte[] body = "{\"status\":\"success\"}".getBytes();
            exchange.sendResponseHeaders(status, status == 204 ? -1 : body.length);
            if (status != 204) {
                exchange.getResponseBody().write(body);
            }
            exchange.close();
        });
        SERVER.start();

        APPENDER = new CapturingAppender();
        APPENDER.start();
        ((Logger) LoggerContext.getContext(false).getLogger(Callback.class.getName())).addAppender(APPENDER);
    }

    @AfterAll
    public static void tearDown() {
        ((Logger) LoggerContext.getContext(false).getLogger(Callback.class.getName())).removeAppender(APPENDER);
        APPENDER.stop();
        SERVER.stop(0);
    }

    @BeforeEach
    public void reset() {
        APPENDER.events.clear();
    }

    @Test
    public void acceptedStatusesAreNotLoggedAsFailure() throws Exception {
        // 202 is what an asynchronous intake API such as PagerDuty's Events API v2 answers with
        for (int status : new int[] {
            200,
            201,
            202,
            204
        }) {
            new Callback().send(status);
        }
        assertTrue(
            APPENDER.events.isEmpty(),
            "a 2xx response means the hook accepted the alarm and must not be logged as a failure, but got: "
                + APPENDER.events
        );
    }

    @Test
    public void nonSuccessStatusIsLoggedAsFailure() throws Exception {
        new Callback().send(500);
        assertEquals(1, APPENDER.events.size());
        assertEquals(Level.ERROR, APPENDER.events.get(0).getLevel());
        assertTrue(APPENDER.events.get(0).getMessage().getFormattedMessage().contains("500"));
    }
}