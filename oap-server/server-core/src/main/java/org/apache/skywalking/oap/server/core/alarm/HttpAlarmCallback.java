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

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import org.apache.skywalking.oap.server.library.util.JdkHttpClientUtils;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public abstract class HttpAlarmCallback implements AlarmCallback {
    private final HttpClient httpClient;

    private final Duration timeout;

    private final AlarmHttpDeliveryMetrics deliveryMetrics;

    private final String hookType;

    /**
     * @param httpClient      shared by every HTTP hook, see {@link #newHttpClient(Duration)}.
     * @param timeout         deadline of each {@link #post(URI, String, Map)}, covering connect, the response headers
     *                        and the response body.
     * @param deliveryMetrics process-local so11y for HTTP deliveries
     * @param hookType        fixed id such as {@code webhook}; never a URL
     */
    protected HttpAlarmCallback(final HttpClient httpClient,
                                final Duration timeout,
                                final AlarmHttpDeliveryMetrics deliveryMetrics,
                                final String hookType) {
        this.httpClient = httpClient;
        this.timeout = timeout;
        this.deliveryMetrics = Objects.requireNonNull(deliveryMetrics, "deliveryMetrics");
        this.hookType = Objects.requireNonNull(hookType, "hookType");
    }

    public static HttpClient newHttpClient(final Duration connectTimeout) {
        return HttpClient
            .newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(connectTimeout)
            .build();
    }

    /**
     * Post {@code body} as one delivery of this hook and record its outcome. A response that is not a success is
     * logged as a failure.
     */
    protected String post(
            final URI uri,
            final String body,
            final Map<String, String> headers)
            throws IOException, InterruptedException {
        final long started = System.nanoTime();
        final HttpResponse<String> response;
        try {
            response = send(uri, body, headers);
        } catch (HttpTimeoutException e) {
            // Must precede IOException: HttpTimeoutException extends IOException.
            record(AlarmHttpDeliveryMetrics.RESULT_TIMEOUT, null, started);
            throw e;
        } catch (IOException e) {
            record(AlarmHttpDeliveryMetrics.RESULT_IO_ERROR, null, started);
            throw e;
        } catch (InterruptedException e) {
            record(AlarmHttpDeliveryMetrics.RESULT_INTERRUPTED, null, started);
            throw e;
        }
        final int status = response.statusCode();
        // Any 2xx means the endpoint accepted the request, unless the platform reports an error in the body.
        // Asynchronous intake APIs answer with 202 rather than 200 (PagerDuty's Events API v2 among them).
        final String result = status >= 200 && status < 300
            ? classifyAcceptedBody(response.body())
            : AlarmHttpDeliveryMetrics.resultOfStatus(status);
        if (!AlarmHttpDeliveryMetrics.RESULT_SUCCESS.equals(result)) {
            LoggerFactory.getLogger(getClass()).error(
                    "send to {} failure. Response code: {}, Response content: {}",
                    uri, status, response.body()
            );
        }
        record(result, AlarmHttpDeliveryMetrics.statusClassOf(status), started);
        return response.body();
    }

    /**
     * Send without recording a delivery, for an auxiliary call such as a WeLink access-token request, whose caller
     * records the outcome through {@link #record(String, String, long)}.
     */
    protected HttpResponse<String> send(
            final URI uri,
            final String body,
            final Map<String, String> headers)
            throws IOException, InterruptedException {
        final var request = HttpRequest
                .newBuilder()
                .uri(uri)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .header("Content-Type", "application/json")
                // JdkHttpClientUtils requires it: only the request's own timeout aborts an exchange still waiting
                // for the response headers.
                .timeout(timeout);
        headers.forEach(request::header);
        return JdkHttpClientUtils.sendStringWithTimeout(httpClient, request.build(), timeout);
    }

    /**
     * Classify the body of a 2xx response. Platforms that answer a rejected message with a 2xx and an error code in
     * the body override this, see {@link #resultOfBodyCode(String, String, Map)}.
     */
    protected String classifyAcceptedBody(final String body) {
        return AlarmHttpDeliveryMetrics.RESULT_SUCCESS;
    }

    /**
     * The result of a JSON body whose {@code codeField} carries the platform's error code: {@link
     * AlarmHttpDeliveryMetrics#RESULT_SUCCESS} when the field is absent or {@code 0}, the result {@code knownCodes}
     * maps the code to, or {@link AlarmHttpDeliveryMetrics#RESULT_REJECTED} for any other code. A body that is not a
     * JSON object is a success, as its 2xx status says.
     */
    protected static String resultOfBodyCode(final String body,
                                             final String codeField,
                                             final Map<String, String> knownCodes) {
        return jsonObjectOf(body)
            .flatMap(json -> stringOf(json, codeField))
            .filter(code -> !"0".equals(code))
            .map(code -> knownCodes.getOrDefault(code, AlarmHttpDeliveryMetrics.RESULT_REJECTED))
            .orElse(AlarmHttpDeliveryMetrics.RESULT_SUCCESS);
    }

    /**
     * {@code body} as a JSON object, or empty when it is not one.
     */
    protected static Optional<JsonObject> jsonObjectOf(final String body) {
        try {
            final JsonElement json = JsonParser.parseString(body);
            return json.isJsonObject() ? Optional.of(json.getAsJsonObject()) : Optional.empty();
        } catch (JsonParseException e) {
            return Optional.empty();
        }
    }

    /**
     * The value of {@code field} as a string, such as {@code "0"} for both {@code 0} and {@code "0"}, or empty when
     * the field is absent or not a primitive.
     */
    protected static Optional<String> stringOf(final JsonObject json, final String field) {
        final JsonElement value = json.get(field);
        return value != null && value.isJsonPrimitive() ? Optional.of(value.getAsString()) : Optional.empty();
    }

    /**
     * Record one delivery outcome of this hook. {@code statusClass} is {@code null} when no response was received, and
     * {@code startedNanos} is the {@link System#nanoTime()} taken when the attempt started.
     */
    protected void record(final String result, final String statusClass, final long startedNanos) {
        deliveryMetrics.record(
            hookType,
            result,
            statusClass,
            (System.nanoTime() - startedNanos) / 1.0E9D
        );
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
}
