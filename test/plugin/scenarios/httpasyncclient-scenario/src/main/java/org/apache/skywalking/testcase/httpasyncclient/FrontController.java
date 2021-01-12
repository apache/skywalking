/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.apache.skywalking.testcase.httpasyncclient;

import java.io.IOException;
import java.nio.CharBuffer;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.apache.http.nio.IOControl;
import org.apache.http.nio.client.methods.AsyncCharConsumer;
import org.apache.http.nio.client.methods.HttpAsyncMethods;
import org.apache.http.nio.protocol.HttpAsyncRequestProducer;
import org.apache.http.protocol.HttpContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/httpasyncclient/case")
public class FrontController {

    private static final Logger LOGGER = LogManager.getLogger(FrontController.class);

    @GetMapping("/healthcheck")
    public String healthcheck() {
        return "Success";
    }

    @GetMapping("/httpasyncclient")
    public String front() throws Exception {
        String content = asyncRequest3("http://127.0.0.1:8080/httpasyncclient/back");
        return content;
    }

    public static final String asyncRequest3(String url) throws IOException {

        final CloseableHttpAsyncClient httpclient = HttpAsyncClients.createDefault();

        httpclient.start();

        final HttpGet request3 = new HttpGet(url);
        HttpAsyncRequestProducer producer3 = HttpAsyncMethods.create(request3);
        AsyncCharConsumer<HttpResponse> consumer3 = new AsyncCharConsumer<HttpResponse>() {
            HttpResponse response;

            @Override
            protected void onResponseReceived(final HttpResponse response) {
                this.response = response;
            }

            @Override
            protected void onCharReceived(final CharBuffer buf, final IOControl ioctrl) throws IOException {
            }

            @Override
            protected void releaseResources() {
            }

            @Override
            protected HttpResponse buildResult(final HttpContext context) {
                return this.response;
            }
        };

        httpclient.execute(producer3, consumer3, new FutureCallback<HttpResponse>() {
            public void completed(final HttpResponse response3) {
                LOGGER.info(request3.getRequestLine() + "->" + response3.getStatusLine());
                try {
                    httpclient.close();
                } catch (IOException e) {
                    LOGGER.error("Httpclient  close failed" + e);
                }
            }

            public void failed(final Exception ex) {
                LOGGER.error(request3.getRequestLine() + "->" + ex);
            }

            public void cancelled() {
                LOGGER.error(request3.getRequestLine() + " cancelled");
            }

        });
        return "Success";
    }
}
