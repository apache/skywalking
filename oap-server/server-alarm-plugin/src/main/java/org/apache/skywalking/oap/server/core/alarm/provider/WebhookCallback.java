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

package org.apache.skywalking.oap.server.core.alarm.provider;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.List;
import org.apache.http.StatusLine;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.skywalking.oap.server.core.alarm.AlarmCallback;
import org.apache.skywalking.oap.server.core.alarm.AlarmMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Use SkyWalking alarm webhook API call a remote endpoints.
 *
 * @author wusheng
 */
public class WebhookCallback implements AlarmCallback {
    private static final Logger logger = LoggerFactory.getLogger(WebhookCallback.class);
    private static final int HTTP_CONNECT_TIMEOUT = 1000;
    private static final int HTTP_CONNECTION_REQUEST_TIMEOUT = 1000;
    private static final int HTTP_SOCKET_TIMEOUT = 10000;

    private List<String> remoteEndpoints;
    private RequestConfig requestConfig;
    private Gson gson = new Gson();

    public WebhookCallback(List<String> remoteEndpoints) {
        this.remoteEndpoints = remoteEndpoints;
        requestConfig = RequestConfig.custom()
            .setConnectTimeout(HTTP_CONNECT_TIMEOUT)
            .setConnectionRequestTimeout(HTTP_CONNECTION_REQUEST_TIMEOUT)
            .setSocketTimeout(HTTP_SOCKET_TIMEOUT).build();
    }

    @Override public void doAlarm(List<AlarmMessage> alarmMessage) {
        if (remoteEndpoints.size() == 0) {
            return;
        }

        CloseableHttpClient httpClient = HttpClients.custom().build();
        try {
            remoteEndpoints.forEach(url -> {
                HttpPost post = new HttpPost(url);
                post.setConfig(requestConfig);
                post.setHeader("Accept", "application/json");
                post.setHeader("Content-type", "application/json");

                StringEntity entity = null;
                try {
                    entity = new StringEntity(gson.toJson(alarmMessage));
                    post.setEntity(entity);
                    CloseableHttpResponse httpResponse = httpClient.execute(post);
                    StatusLine statusLine = httpResponse.getStatusLine();
                    if (statusLine != null && statusLine.getStatusCode() != 200) {
                        logger.error("send alarm to " + url + " failure. Response code: " + statusLine.getStatusCode());
                    }
                } catch (UnsupportedEncodingException e) {
                    logger.error("Alarm to JSON error, " + e.getMessage(), e);
                } catch (ClientProtocolException e) {
                    logger.error("send alarm to " + url + " failure.", e);
                } catch (IOException e) {
                    logger.error("send alarm to " + url + " failure.", e);
                }
            });
        } finally {
            try {
                httpClient.close();
            } catch (IOException e) {
                logger.error(e.getMessage(), e);
            }
        }
    }
}
