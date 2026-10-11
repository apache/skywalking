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

import org.apache.skywalking.oap.server.library.util.JdkHttpClientUtils;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public abstract class HttpAlarmCallback implements AlarmCallback {
    /**
     * Default end-to-end HTTP delivery deadline (seconds), including reading the response body.
     * Overridable via {@code alarm.default.httpTimeout} / {@code SW_ALARM_HTTP_TIMEOUT}.
     * The same duration is applied to {@link HttpClient.Builder#connectTimeout(Duration)} and
     * {@link HttpRequest.Builder#timeout(Duration)}. Body consumption is bounded by
     * {@link JdkHttpClientUtils}, because {@code HttpRequest} timeout stops when the response
     * headers arrive. The explicit connect timeout still fails a slow or unreachable webhook
     * with {@link java.net.http.HttpConnectTimeoutException}, so it does not hold the single
     * AlarmCore delivery thread during connect longer than this deadline.
     */
    private static final long DEFAULT_HTTP_TIMEOUT_SECONDS = 12;

    /**
     * Upper bound so {@link Duration#toNanos()} does not overflow when the shared deadline is applied.
     */
    private static final long MAX_HTTP_TIMEOUT_SECONDS = TimeUnit.NANOSECONDS.toSeconds(Long.MAX_VALUE);

    /**
     * One published pair of timeout + client. {@link #configure(long)} replaces the whole
     * reference so {@link #post(URI, String, Map)} always observes a matched connect timeout
     * and request/body deadline.
     */
    private static volatile HttpDeliverySettings SETTINGS = HttpDeliverySettings.ofSeconds(DEFAULT_HTTP_TIMEOUT_SECONDS);

    private static HttpClient newHttpClient(final Duration connectTimeout) {
        return HttpClient
            .newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(connectTimeout)
            .build();
    }

    /**
     * Apply HTTP timeouts from alarm module configuration. Values {@code <= 0} fall back to
     * {@link #DEFAULT_HTTP_TIMEOUT_SECONDS}. Values above {@link #MAX_HTTP_TIMEOUT_SECONDS} are
     * capped so {@link Duration#toNanos()} cannot overflow. The same duration is the end-to-end
     * delivery deadline, including connect and reading the response body.
     */
    public static synchronized void configure(final long httpTimeoutSeconds) {
        long seconds = httpTimeoutSeconds > 0 ? httpTimeoutSeconds : DEFAULT_HTTP_TIMEOUT_SECONDS;
        if (seconds > MAX_HTTP_TIMEOUT_SECONDS) {
            seconds = MAX_HTTP_TIMEOUT_SECONDS;
        }
        SETTINGS = HttpDeliverySettings.ofSeconds(seconds);
    }

    protected String post(
            final URI uri,
            final String body,
            final Map<String, String> headers)
            throws IOException, InterruptedException {
        final HttpDeliverySettings settings = SETTINGS;
        final Duration timeout = settings.timeout;
        final var request = HttpRequest
                .newBuilder()
                .uri(uri)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .header("Content-Type", "application/json")
                .timeout(timeout);
        headers.forEach(request::header);

        final var response = JdkHttpClientUtils.sendStringWithTimeout(settings.client, request.build(), timeout);

        final var status = response.statusCode();
        // Any 2xx means the hook endpoint accepted the alarm. Asynchronous intake APIs answer with 202 rather
        // than 200 (PagerDuty's Events API v2 among them), which previously logged every delivery as a failure.
        if (status < 200 || status >= 300) {
            final var logger = LoggerFactory.getLogger(getClass());
            logger.error(
                    "send to {} failure. Response code: {}, Response content: {}",
                    uri, status, response.body()
            );
        }
        return response.body();
    }

    /**
     * Send alarm message if the settings not empty
     */
    public void doAlarm(List<AlarmMessage> alarmMessages) throws Exception {
        doAlarmCallback(alarmMessages, false);
    }

    /**
     * Send alarm  recovery message if the settings not empty
     */
    public void doAlarmRecovery(List<AlarmMessage> alarmRecoveryMessages) throws Exception {
        doAlarmCallback(alarmRecoveryMessages, true);
    }

    protected abstract void doAlarmCallback(List<AlarmMessage> alarmMessages, boolean isRecovery) throws Exception ;

    private static final class HttpDeliverySettings {
        private final Duration timeout;

        private final HttpClient client;

        private HttpDeliverySettings(final Duration timeout, final HttpClient client) {
            this.timeout = timeout;
            this.client = client;
        }

        private static HttpDeliverySettings ofSeconds(final long seconds) {
            final Duration timeout = Duration.ofSeconds(seconds);
            return new HttpDeliverySettings(timeout, newHttpClient(timeout));
        }
    }
}
