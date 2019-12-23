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

package org.apache.skywalking.apm.testcase.servicecomb.consumer;

import io.servicecomb.foundation.common.utils.BeanUtils;
import io.servicecomb.foundation.common.utils.Log4jUtils;
import org.apache.http.HttpEntity;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class CodeFirstConsumerMain {

    private static Logger LOGGER = Logger.getLogger(CodeFirstConsumerMain.class);

    public static void main(String[] args) {
        waitProvider();
        init();
    }

    public static void init() {
        while (true) {
            try {
                Log4jUtils.init();
                BeanUtils.init();
                return;
            } catch (Throwable e) {
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException ex) {
                    // ignore
                }
            }
        }
    }

    private static void waitProvider() {
        int index = 0;
        while (true) {
            try {
                visit("http://127.0.0.1:8080/");
                return;
            } catch (Throwable e) {
                try {
                    Thread.sleep(1000);
                    if (++index % 10 == 0) {
                        LOGGER.error(e.getMessage(), e);
                    }
                } catch (InterruptedException ex) {
                    // ignore
                }
            }
        }
    }

    private static void visit(String url) throws IOException {
        CloseableHttpClient httpClient = HttpClients.createDefault();
        try {
            HttpGet httpget = new HttpGet(url);
            ResponseHandler<String> responseHandler = response -> {
                HttpEntity entity = response.getEntity();
                return entity != null ? EntityUtils.toString(entity) : null;
            };
            httpClient.execute(httpget, responseHandler);
        } finally {
            httpClient.close();
        }
    }

}
