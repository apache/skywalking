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

package org.apache.skywalking.oap.server.storage.plugin.banyandb;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.skywalking.apm.network.language.agent.v3.BrowserErrorLog;
import org.apache.skywalking.apm.network.language.agent.v3.ErrorCategory;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.browser.manual.errorlog.BrowserErrorLogRecord;
import org.apache.skywalking.oap.server.core.browser.source.BrowserErrorCategory;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.type.BrowserErrorLogs;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.stream.BanyanDBBrowserLogQueryDAO;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

/**
 * Reads browser error logs the way the browser log query does, on the real {@code browser_error_log} stream: 30 logs
 * of one service in one bulk, the first and the last matching each filter — version, page, category — so a filter
 * applied after the {@code limit + offset} cap would lose the oldest match. Each log comes back whole.
 */
@Testcontainers
public class BanyanDBBrowserLogQueryIT {
    private static final int LOGS = 30;
    private static final String SERVICE_ID = IDManager.ServiceID.buildId("browser-it", true);
    private static final String READ_VERSION = IDManager.ServiceInstanceID.buildId(SERVICE_ID, "v-read");
    private static final String OTHER_VERSION = IDManager.ServiceInstanceID.buildId(SERVICE_ID, "v-other");
    private static final String READ_PAGE = IDManager.EndpointID.buildId(SERVICE_ID, "/read");
    private static final String OTHER_PAGE = IDManager.EndpointID.buildId(SERVICE_ID, "/other");

    @Container
    private static final GenericContainer<?> BANYANDB = BanyanDBQueryITSupport.banyanDB();

    private static BanyanDBQueryITSupport SUPPORT;
    private static BanyanDBBrowserLogQueryDAO QUERY_DAO;
    private static Duration DURATION;
    private static long FIRST;

    @BeforeAll
    public static void writeLogs() throws Exception {
        SUPPORT = BanyanDBQueryITSupport.connect(BANYANDB);
        final Model model = SUPPORT.install(
            BrowserErrorLogRecord.class, DefaultScopeDefine.BROWSER_ERROR_LOG, BrowserErrorLogRecord.INDEX_NAME);
        FIRST = System.currentTimeMillis() - LOGS * 1000L;
        final List<BrowserErrorLogRecord> logs = new ArrayList<>();
        for (int i = 0; i < LOGS; i++) {
            logs.add(log(i));
        }
        SUPPORT.write(model, new BrowserErrorLogRecord.Builder(), logs);
        QUERY_DAO = new BanyanDBBrowserLogQueryDAO(SUPPORT.getClient());
        DURATION = BanyanDBQueryITSupport.around(FIRST, FIRST + LOGS * 1000L);
        await().atMost(30, TimeUnit.SECONDS)
               .untilAsserted(() -> assertEquals(LOGS, read(SERVICE_ID, null, null, null, LOGS * 2, 0).size()));
    }

    @AfterAll
    public static void close() throws IOException {
        if (SUPPORT != null) {
            SUPPORT.close();
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("filters")
    public void findsBothEndsInOnePage(String filter, String version, String page, BrowserErrorCategory category)
        throws IOException {
        assertEquals(Arrays.asList(message(LOGS - 1), message(0)), read(SERVICE_ID, version, page, category, 2, 0));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("filters")
    public void pagesThroughTheMatches(String filter, String version, String page, BrowserErrorCategory category)
        throws IOException {
        assertEquals(Collections.singletonList(message(LOGS - 1)), read(SERVICE_ID, version, page, category, 1, 0));
        assertEquals(Collections.singletonList(message(0)), read(SERVICE_ID, version, page, category, 1, 1));
        assertEquals(Collections.emptyList(), read(SERVICE_ID, version, page, category, 1, 2));
    }

    static Stream<Arguments> filters() {
        return Stream.of(
            Arguments.of("version", READ_VERSION, null, null),
            Arguments.of("page", null, READ_PAGE, null),
            Arguments.of("category", null, null, BrowserErrorCategory.JS),
            Arguments.of("all three", READ_VERSION, READ_PAGE, BrowserErrorCategory.JS)
        );
    }

    /** Each log's message, newest first. */
    private static List<String> read(String service, String version, String page, BrowserErrorCategory category,
                                     int limit, int from) throws IOException {
        final BrowserErrorLogs logs = QUERY_DAO.queryBrowserErrorLogs(service, version, page, category, DURATION, limit, from);
        return logs.getLogs().stream()
                   .map(l -> l.getMessage() + "|" + l.getPagePath() + "|" + l.getCategory())
                   .collect(Collectors.toList());
    }

    private static String message(int i) {
        final boolean read = i == 0 || i == LOGS - 1;
        return "error " + i + "|" + (read ? "/read" : "/other") + "|" + (read ? "JS" : "AJAX");
    }

    private static BrowserErrorLogRecord log(int i) {
        final boolean read = i == 0 || i == LOGS - 1;
        final long timestamp = FIRST + i * 1000L;
        final BrowserErrorLogRecord record = new BrowserErrorLogRecord();
        record.setUniqueId("browser-error-" + i);
        record.setServiceId(SERVICE_ID);
        record.setServiceVersionId(read ? READ_VERSION : OTHER_VERSION);
        record.setPagePathId(read ? READ_PAGE : OTHER_PAGE);
        record.setErrorCategory(read ? BrowserErrorCategory.JS.getValue() : BrowserErrorCategory.AJAX.getValue());
        record.setTimestamp(timestamp);
        record.setTimeBucket(TimeBucket.getRecordTimeBucket(timestamp));
        record.setDataBinary(BrowserErrorLog.newBuilder()
                                            .setUniqueId("browser-error-" + i)
                                            .setService("browser-it")
                                            .setServiceVersion(read ? "v-read" : "v-other")
                                            .setPagePath(read ? "/read" : "/other")
                                            .setCategory(read ? ErrorCategory.js : ErrorCategory.ajax)
                                            .setMessage("error " + i)
                                            .setTime(timestamp)
                                            .build().toByteArray());
        return record;
    }
}
