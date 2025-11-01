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

import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

public abstract class HttpAlarmCallback implements AlarmCallback {
    protected String post(
            final URI uri,
            final String body,
            final Map<String, String> headers)
            throws IOException, InterruptedException {
        final var request = HttpRequest
                .newBuilder()
                .uri(uri)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(12));
        headers.forEach(request::header);

        final var response = HttpClient
                .newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build()
                .send(request.build(), HttpResponse.BodyHandlers.ofString());

        final var status = response.statusCode();
        if (status != 200 && status != 204) {
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

}
