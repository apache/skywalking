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

package org.apache.skywalking.apm.collector.agent.jetty.provider;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import org.apache.http.Consts;
import org.apache.http.HttpEntity;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public enum HttpClientTools {
    INSTANCE;

    private static final Logger logger = LoggerFactory.getLogger(HttpClientTools.class);

    public String get(String url, List<NameValuePair> params) throws IOException {
        CloseableHttpClient httpClient = HttpClients.createDefault();
        try {
            HttpGet httpget = new HttpGet(url);
            String paramStr = EntityUtils.toString(new UrlEncodedFormEntity(params));
            httpget.setURI(new URI(httpget.getURI().toString() + "?" + paramStr));
            logger.debug("executing get request {}", httpget.getURI());

            try (CloseableHttpResponse response = httpClient.execute(httpget)) {
                HttpEntity entity = response.getEntity();
                if (entity != null) {
                    return EntityUtils.toString(entity);
                }
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        } finally {
            try {
                httpClient.close();
            } catch (IOException e) {
                logger.error(e.getMessage(), e);
            }
        }
        return null;
    }

    public String post(String url, String data) throws IOException {
        CloseableHttpClient httpClient = HttpClients.createDefault();
        try {
            HttpPost httppost = new HttpPost(url);
            httppost.setEntity(new StringEntity(data, Consts.UTF_8));
            logger.debug("executing post request {}", httppost.getURI());
            try (CloseableHttpResponse response = httpClient.execute(httppost)) {
                HttpEntity entity = response.getEntity();
                if (entity != null) {
                    return EntityUtils.toString(entity);
                }
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        } finally {
            try {
                httpClient.close();
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
            }
        }
        return null;
    }
}
