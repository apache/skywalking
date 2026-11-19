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

package org.apache.skywalking.oap.server.storage.plugin.elasticsearch.query;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.skywalking.apm.network.language.agent.v3.BrowserErrorLog;
import org.apache.skywalking.apm.network.language.agent.v3.ErrorCategory;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.browser.manual.errorlog.BrowserErrorLogRecord;
import org.apache.skywalking.oap.server.core.browser.source.BrowserErrorCategory;
import org.apache.skywalking.oap.server.core.query.enumeration.Step;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.type.BrowserErrorLogs;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.ElasticSearchQueryITSupport;
import org.joda.time.DateTime;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Reads browser error logs the way the browser log query does on Elasticsearch and OpenSearch, through
 * {@link BrowserLogQueryEsDAO}: by service, version, page and error category, newest first, page by page, with or
 * without a duration. A nested class of a server's IT names the server and the storage mode.
 *
 * <p>The errors are written as the browser receiver writes them, on both sides of midnight, so a read spans two daily
 * indices. For each filter an error matches every other condition but that one. Errors of their own lie on and just
 * outside the bounds of a duration: on its first millisecond and the one before, on its last millisecond, and just
 * after its end.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class BrowserLogQueryITBase {
    /** A fixed local midnight, so the daily indices the errors are written to are known. */
    private static final long MIDNIGHT = new DateTime(2026, 1, 15, 0, 0).getMillis();
    private static final long MINUTE = 60_000L;
    private static final Duration AROUND_MIDNIGHT = minutes(MIDNIGHT - 5 * MINUTE, MIDNIGHT + 4 * MINUTE);
    private static final Duration BOUNDED = minutes(MIDNIGHT + 10 * MINUTE, MIDNIGHT + 11 * MINUTE);

    private static final String SHOP = "shop-web";
    private static final String ADMIN = "admin-web";
    private static final String BOUNDS = "bounds-web";

    private static final List<ErrorLog> ERRORS = Arrays.asList(
        new ErrorLog("e1", MIDNIGHT - 150_000L, SHOP, "v1", "/cart", ErrorCategory.js),
        new ErrorLog("e2", MIDNIGHT - 90_000L, SHOP, "v1", "/cart", ErrorCategory.ajax),
        new ErrorLog("e3", MIDNIGHT - 30_000L, SHOP, "v2", "/cart", ErrorCategory.js),
        new ErrorLog("e4", MIDNIGHT + 30_000L, SHOP, "v1", "/checkout", ErrorCategory.js),
        new ErrorLog("e5", MIDNIGHT + 90_000L, SHOP, "v1", "/cart", ErrorCategory.js),
        new ErrorLog("e6", MIDNIGHT + 150_000L, ADMIN, "v1", "/cart", ErrorCategory.js),
        new ErrorLog("before-start", BOUNDED.getStartTimestamp() - 1, BOUNDS, "v1", "/", ErrorCategory.resource),
        new ErrorLog("on-start", BOUNDED.getStartTimestamp(), BOUNDS, "v1", "/", ErrorCategory.resource),
        new ErrorLog("last-millisecond", BOUNDED.getEndTimestamp() - 1, BOUNDS, "v1", "/", ErrorCategory.resource),
        new ErrorLog("after-end", BOUNDED.getEndTimestamp() + 1, BOUNDS, "v1", "/", ErrorCategory.resource)
    );

    private final ElasticsearchContainer server;
    private final ElasticSearchQueryITSupport.Mode mode;
    private ElasticSearchQueryITSupport support;
    private BrowserLogQueryEsDAO dao;

    BrowserLogQueryITBase(final ElasticsearchContainer server, final ElasticSearchQueryITSupport.Mode mode) {
        this.server = server;
        this.mode = mode;
    }

    @BeforeAll
    public void writeErrors() throws Exception {
        support = ElasticSearchQueryITSupport.connect(server, mode, getClass());
        final Model model = support.installRecord(BrowserErrorLogRecord.class);
        support.writeRecords(model, new BrowserErrorLogRecord.Builder(), ERRORS.stream()
            .map(ErrorLog::record).collect(Collectors.toList()));
        dao = new BrowserLogQueryEsDAO(support.getClient());
    }

    @AfterAll
    public void close() {
        if (support != null) {
            support.close();
        }
    }

    @Test
    public void readsTheErrorsOfAServiceNewestFirst() throws IOException {
        assertEquals(Arrays.asList("e5", "e4", "e3", "e2", "e1"), ids(serviceId(SHOP), null, null, null));
        assertEquals(Collections.singletonList("e6"), ids(serviceId(ADMIN), null, null, null));
    }

    @Test
    public void readsTheErrorsOfAVersion() throws IOException {
        assertEquals(Arrays.asList("e5", "e4", "e2", "e1"), ids(null, versionId(SHOP, "v1"), null, null));
    }

    @Test
    public void readsTheErrorsOfAPage() throws IOException {
        assertEquals(Arrays.asList("e5", "e3", "e2", "e1"), ids(null, null, pageId(SHOP, "/cart"), null));
    }

    @Test
    public void readsTheErrorsOfACategory() throws IOException {
        assertEquals(Arrays.asList("e6", "e5", "e4", "e3", "e1"), ids(null, null, null, BrowserErrorCategory.JS));
    }

    @Test
    public void readsTheErrorsMatchingEveryFilter() throws IOException {
        assertEquals(Arrays.asList("e5", "e1"), ids(
            serviceId(SHOP), versionId(SHOP, "v1"), pageId(SHOP, "/cart"), BrowserErrorCategory.JS));
    }

    @Test
    public void pagesThroughTheErrorsOfAService() throws IOException {
        assertEquals(Arrays.asList("e5", "e4"), page(0, 2));
        assertEquals(Arrays.asList("e3", "e2"), page(2, 2));
        assertEquals(Collections.singletonList("e1"), page(4, 2));
        assertEquals(Collections.emptyList(), page(6, 2));
    }

    @Test
    public void readsEachErrorWhole() throws IOException {
        final List<String> expected = new ArrayList<>();
        for (int i = 5; i >= 0; i--) {
            expected.add(ERRORS.get(i).render());
        }
        assertEquals(expected, dao.queryBrowserErrorLogs(null, null, null, null, AROUND_MIDNIGHT, 10, 0)
                                  .getLogs().stream().map(BrowserLogQueryITBase::render)
                                  .collect(Collectors.toList()));
    }

    @Test
    public void readsTheErrorsOfEveryDayWithoutADuration() throws IOException {
        assertEquals(Arrays.asList("e5", "e4", "e2", "e1"), messages(
            dao.queryBrowserErrorLogs(null, versionId(SHOP, "v1"), null, null, null, 10, 0)));
        final List<String> everyBound = Arrays.asList(
            "after-end", "last-millisecond", "on-start", "before-start");
        assertEquals(everyBound, messages(dao.queryBrowserErrorLogs(serviceId(BOUNDS), null, null, null, null, 10, 0)));
    }

    @Test
    public void keepsTheErrorsWithinTheDuration() throws IOException {
        assertEquals(Arrays.asList("last-millisecond", "on-start"), messages(
            dao.queryBrowserErrorLogs(serviceId(BOUNDS), null, null, null, BOUNDED, 10, 0)));
    }

    private List<String> ids(final String serviceId, final String versionId, final String pageId,
                             final BrowserErrorCategory category) throws IOException {
        return messages(dao.queryBrowserErrorLogs(serviceId, versionId, pageId, category, AROUND_MIDNIGHT, 10, 0));
    }

    private List<String> page(final int from, final int limit) throws IOException {
        return messages(dao.queryBrowserErrorLogs(serviceId(SHOP), null, null, null, AROUND_MIDNIGHT, limit, from));
    }

    /** Each error's message is its id. */
    private static List<String> messages(final BrowserErrorLogs logs) {
        return logs.getLogs().stream().map(log -> log.getMessage()).collect(Collectors.toList());
    }

    private static String render(final org.apache.skywalking.oap.server.core.query.type.BrowserErrorLog log) {
        return String.join("|", log.getService(), log.getServiceVersion(), String.valueOf(log.getTime()),
                           log.getPagePath(), log.getCategory().name(), log.getGrade(), log.getMessage(),
                           String.valueOf(log.getLine()), String.valueOf(log.getCol()), log.getStack(),
                           log.getErrorUrl(), String.valueOf(log.isFirstReportedError()));
    }

    private static Duration minutes(final long start, final long end) {
        final Duration duration = new Duration();
        duration.setStep(Step.MINUTE);
        duration.setStart(new DateTime(start).toString("yyyy-MM-dd HHmm"));
        duration.setEnd(new DateTime(end).toString("yyyy-MM-dd HHmm"));
        return duration;
    }

    private static String serviceId(final String service) {
        return IDManager.ServiceID.buildId(service, true);
    }

    private static String versionId(final String service, final String version) {
        return IDManager.ServiceInstanceID.buildId(serviceId(service), version);
    }

    private static String pageId(final String service, final String page) {
        return IDManager.EndpointID.buildId(serviceId(service), page);
    }

    /**
     * A browser error log as the browser error log receiver hands it to the record stream.
     */
    private static final class ErrorLog {
        private final String id;
        private final long time;
        private final String service;
        private final String version;
        private final String page;
        private final ErrorCategory category;

        ErrorLog(final String id, final long time, final String service, final String version, final String page,
                 final ErrorCategory category) {
            this.id = id;
            this.time = time;
            this.service = service;
            this.version = version;
            this.page = page;
            this.category = category;
        }

        BrowserErrorLogRecord record() {
            final BrowserErrorLogRecord record = new BrowserErrorLogRecord();
            record.setUniqueId(id);
            record.setServiceId(serviceId(service));
            record.setServiceVersionId(versionId(service, version));
            record.setPagePathId(pageId(service, page));
            record.setErrorCategory(BrowserErrorCategory.valueOf(category.name().toUpperCase()).getValue());
            record.setTimestamp(time);
            record.setTimeBucket(TimeBucket.getRecordTimeBucket(time));
            record.setDataBinary(BrowserErrorLog.newBuilder()
                                                .setUniqueId(id)
                                                .setService(service)
                                                .setServiceVersion(version)
                                                .setTime(time)
                                                .setPagePath(page)
                                                .setCategory(category)
                                                .setGrade("error")
                                                .setMessage(id)
                                                .setLine(12)
                                                .setCol(34)
                                                .setStack("at " + page)
                                                .setErrorUrl("https://shop.example.com" + page + ".js")
                                                .setFirstReportedError("e1".equals(id))
                                                .build().toByteArray());
            return record;
        }

        String render() {
            return String.join("|", service, version, String.valueOf(time), page, category.name().toUpperCase(),
                               "error", id, "12", "34", "at " + page, "https://shop.example.com" + page + ".js",
                               String.valueOf("e1".equals(id)));
        }
    }
}
