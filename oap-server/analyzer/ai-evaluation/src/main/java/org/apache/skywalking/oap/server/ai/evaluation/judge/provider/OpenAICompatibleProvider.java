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

package org.apache.skywalking.oap.server.ai.evaluation.judge.provider;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.Properties;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.skywalking.oap.server.ai.evaluation.judge.JudgeModelException;
import org.apache.skywalking.oap.server.ai.evaluation.judge.JudgeModelException.Reason;
import org.apache.skywalking.oap.server.ai.evaluation.judge.JudgeModelProvider;
import org.apache.skywalking.oap.server.ai.evaluation.judge.JudgeModelRequest;
import org.apache.skywalking.oap.server.ai.evaluation.judge.JudgeModelResponse;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.util.PropertyPlaceholderHelper;
import org.apache.skywalking.oap.server.library.util.StringUtil;

public class OpenAICompatibleProvider implements JudgeModelProvider {
    private static final Gson GSON = new Gson();
    private static final long DEFAULT_REQUEST_TIMEOUT_SECONDS = 30L;
    private static final int DEFAULT_MAX_RETRIES = 2;
    private static final int MAX_CONFIGURED_RETRIES = 5;
    private static final long DEFAULT_RETRY_DELAY_MILLIS = 200L;
    private static final long MAX_RETRY_DELAY_MILLIS = 5_000L;
    private static final double MIN_TEMPERATURE = 0.0;
    private static final double MAX_TEMPERATURE = 1.0;

    private final HttpClient httpClient;
    private final String endpoint;
    private final String apiKey;
    private final String model;
    private final Duration requestTimeout;
    private final int maxRetries;
    private final Double temperature;
    private final Integer maxTokens;

    public OpenAICompatibleProvider(final Properties config) throws ModuleStartException {
        this(HttpClient.newHttpClient(), config);
    }

    OpenAICompatibleProvider(final HttpClient httpClient, final Properties config) throws ModuleStartException {
        validate(config);
        this.httpClient = httpClient;
        this.endpoint = getString(config, "endpoint");
        this.apiKey = getString(config, "api-key");
        this.model = getString(config, "model");
        this.requestTimeout = Duration.ofSeconds(getRequestTimeoutSeconds(config));
        this.maxRetries = getMaxRetries(config);
        this.temperature = getDouble(config, "temperature");
        this.maxTokens = getInteger(config, "max_tokens");
    }

    @Override
    public JudgeModelResponse judge(final JudgeModelRequest request)
            throws IOException, InterruptedException {
        final HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(requestTimeout)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(buildRequestBody(request)))
                .build();
        for (int retry = 0; ; retry++) {
            final HttpResponse<String> response;
            try {
                response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            } catch (HttpTimeoutException e) {
                if (retry >= maxRetries) {
                    throw new JudgeModelException(Reason.TIMEOUT, "OpenAI compatible judge API request timed out.", e);
                }
                waitBeforeRetry(defaultRetryDelay(retry));
                continue;
            } catch (IOException e) {
                throw new JudgeModelException(
                    Reason.REJECTED,
                    "OpenAI compatible judge API request failed before receiving a response.",
                    e
                );
            }

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return parseSuccessfulResponse(response.body());
            }
            if (isRetryable(response.statusCode()) && retry < maxRetries) {
                waitBeforeRetry(retryDelay(response, retry));
                continue;
            }
            throw new JudgeModelException(
                Reason.REJECTED,
                "OpenAI compatible judge API request rejected, status: " + response.statusCode()
            );
        }
    }

    @Override
    public String model() {
        return model;
    }

    private static void validate(final Properties config) throws ModuleStartException {
        if (StringUtil.isBlank(getString(config, "endpoint"))) {
            throw new ModuleStartException("AI evaluation judge config [endpoint] is required.");
        }
        if (StringUtil.isBlank(getString(config, "model"))) {
            throw new ModuleStartException("AI evaluation judge config [model] is required.");
        }
        if (StringUtil.isBlank(getString(config, "api-key"))) {
            throw new ModuleStartException("AI evaluation judge config [api-key] is required.");
        }
        validateRequestTimeoutSeconds(getString(config, "request-timeout-seconds"));
        validateMaxRetries(getString(config, "max-retries"));
        validateTemperature(getString(config, "temperature"));
        validateMaxTokens(getString(config, "max_tokens"));
    }

    private String buildRequestBody(final JudgeModelRequest request) {
        final JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.addProperty("stream", false);
        if (temperature != null) {
            body.addProperty("temperature", temperature);
        }
        if (maxTokens != null) {
            body.addProperty("max_tokens", maxTokens);
        }

        final JsonArray messages = new JsonArray();
        addMessage(messages, "system", request.getSystemPrompt());
        addMessage(messages, "user", request.getUserPrompt());
        body.add("messages", messages);
        return GSON.toJson(body);
    }

    private static void addMessage(final JsonArray messages, final String role, final String content) {
        if (StringUtil.isBlank(content)) {
            return;
        }
        final JsonObject message = new JsonObject();
        message.addProperty("role", role);
        message.addProperty("content", content);
        messages.add(message);
    }

    private static JudgeModelResponse parseSuccessfulResponse(final String body)
            throws JudgeModelException {
        final JudgeModelResponse judgeResponse;
        try {
            judgeResponse = parseResponse(body);
        } catch (RuntimeException e) {
            throw new JudgeModelException(
                Reason.INVALID_RESPONSE,
                "OpenAI compatible judge API returned a malformed response.",
                e
            );
        }
        if (StringUtil.isBlank(judgeResponse.getContent())) {
            throw new JudgeModelException(
                Reason.INVALID_RESPONSE,
                "OpenAI compatible judge API response has no completion content."
            );
        }
        return judgeResponse;
    }

    private static boolean isRetryable(final int statusCode) {
        return statusCode == 429 || statusCode >= 500 && statusCode < 600;
    }

    private static long retryDelay(final HttpResponse<?> response, final int retry) {
        final Optional<String> retryAfter = response.headers().firstValue("Retry-After");
        if (retryAfter.isPresent()) {
            final long parsed = parseRetryAfter(retryAfter.get());
            if (parsed >= 0) {
                return parsed;
            }
        }
        return defaultRetryDelay(retry);
    }

    private static long parseRetryAfter(final String value) {
        try {
            final long seconds = Long.parseLong(value.trim());
            if (seconds < 0) {
                return -1;
            }
            if (seconds >= MAX_RETRY_DELAY_MILLIS / 1_000L) {
                return MAX_RETRY_DELAY_MILLIS;
            }
            return seconds * 1_000L;
        } catch (NumberFormatException ignored) {
            try {
                final Instant retryAt = ZonedDateTime.parse(
                    value.trim(), DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
                final long delay = Duration.between(Instant.now(), retryAt).toMillis();
                return Math.min(Math.max(delay, 0L), MAX_RETRY_DELAY_MILLIS);
            } catch (DateTimeParseException invalidDate) {
                return -1;
            }
        }
    }

    private static long defaultRetryDelay(final int retry) {
        return Math.min(DEFAULT_RETRY_DELAY_MILLIS << retry, MAX_RETRY_DELAY_MILLIS);
    }

    private static void waitBeforeRetry(final long delayMillis) throws InterruptedException {
        if (delayMillis > 0) {
            Thread.sleep(delayMillis);
        }
    }

    private static JudgeModelResponse parseResponse(final String body) {
        final JsonObject root = JsonParser.parseString(body).getAsJsonObject();
        final JsonArray choices = root.getAsJsonArray("choices");
        String content = "";
        if (choices != null && choices.size() > 0) {
            final JsonObject choice = choices.get(0).getAsJsonObject();
            final JsonObject message = choice.getAsJsonObject("message");
            if (message != null) {
                content = getAsString(message, "content");
            }
        }

        final JsonObject usage = root.getAsJsonObject("usage");
        return JudgeModelResponse.builder()
                .content(content)
                .promptTokens(getAsInt(usage, "prompt_tokens"))
                .completionTokens(getAsInt(usage, "completion_tokens"))
                .totalTokens(getAsInt(usage, "total_tokens"))
                .build();
    }

    private static String getAsString(final JsonObject object, final String memberName) {
        if (object == null) {
            return "";
        }
        final JsonElement element = object.get(memberName);
        return element == null || element.isJsonNull() ? "" : element.getAsString();
    }

    private static int getAsInt(final JsonObject object, final String memberName) {
        if (object == null) {
            return 0;
        }
        final JsonElement element = object.get(memberName);
        return element == null || element.isJsonNull() ? 0 : element.getAsInt();
    }

    private static String getString(final Properties properties, final String key) {
        if (properties == null) {
            return null;
        }
        final Object value = properties.get(key);
        if (value == null) {
            return null;
        }
        return PropertyPlaceholderHelper.INSTANCE.replacePlaceholders(String.valueOf(value), properties);
    }

    private static Double getDouble(final Properties properties, final String key) throws ModuleStartException {
        final String value = getString(properties, key);
        if (StringUtil.isBlank(value)) {
            return null;
        }
        try {
            return Double.valueOf(value);
        } catch (NumberFormatException e) {
            throw new ModuleStartException("AI evaluation judge config [" + key + "] must be a number.", e);
        }
    }

    private static Integer getInteger(final Properties properties, final String key) throws ModuleStartException {
        final String value = getString(properties, key);
        if (StringUtil.isBlank(value)) {
            return null;
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException e) {
            throw new ModuleStartException("AI evaluation judge config [" + key + "] must be an integer.", e);
        }
    }

    private static long getRequestTimeoutSeconds(final Properties properties) throws ModuleStartException {
        final String value = getString(properties, "request-timeout-seconds");
        if (StringUtil.isBlank(value)) {
            return DEFAULT_REQUEST_TIMEOUT_SECONDS;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new ModuleStartException(
                    "AI evaluation judge config [request-timeout-seconds] must be an integer.",
                    e
            );
        }
    }

    private static int getMaxRetries(final Properties properties) throws ModuleStartException {
        final String value = getString(properties, "max-retries");
        if (StringUtil.isBlank(value)) {
            return DEFAULT_MAX_RETRIES;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new ModuleStartException("AI evaluation judge config [max-retries] must be an integer.", e);
        }
    }

    private static void validateRequestTimeoutSeconds(final String value) throws ModuleStartException {
        if (StringUtil.isBlank(value)) {
            return;
        }
        final long parsed;
        try {
            parsed = Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new ModuleStartException(
                    "AI evaluation judge config [request-timeout-seconds] must be an integer.",
                    e
            );
        }
        if (parsed <= 0) {
            throw new ModuleStartException(
                    "AI evaluation judge config [request-timeout-seconds] must be greater than 0."
            );
        }
    }

    private static void validateMaxRetries(final String value) throws ModuleStartException {
        if (StringUtil.isBlank(value)) {
            return;
        }
        final int parsed;
        try {
            parsed = Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new ModuleStartException("AI evaluation judge config [max-retries] must be an integer.", e);
        }
        if (parsed < 0 || parsed > MAX_CONFIGURED_RETRIES) {
            throw new ModuleStartException(
                "AI evaluation judge config [max-retries] must be between 0 and "
                    + MAX_CONFIGURED_RETRIES + '.'
            );
        }
    }

    private static void validateTemperature(final String value) throws ModuleStartException {
        if (StringUtil.isBlank(value)) {
            return;
        }
        final double parsed;
        try {
            parsed = Double.parseDouble(value);
        } catch (NumberFormatException e) {
            throw new ModuleStartException("AI evaluation judge config [temperature] must be a number.", e);
        }
        if (parsed < MIN_TEMPERATURE || parsed > MAX_TEMPERATURE) {
            throw new ModuleStartException(
                    "AI evaluation judge config [temperature] must be greater than 0 and less than or equal to "
                            + MAX_TEMPERATURE + '.'
            );
        }
    }

    private static void validateMaxTokens(final String value) throws ModuleStartException {
        if (StringUtil.isBlank(value)) {
            return;
        }
        final int parsed;
        try {
            parsed = Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new ModuleStartException("AI evaluation judge config [max_tokens] must be an integer.", e);
        }
        if (parsed <= 0) {
            throw new ModuleStartException("AI evaluation judge config [max_tokens] must be greater than 0.");
        }
    }
}
