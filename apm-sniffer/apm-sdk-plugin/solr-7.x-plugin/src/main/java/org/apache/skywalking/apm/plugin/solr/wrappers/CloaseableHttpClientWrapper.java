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

package org.apache.skywalking.apm.plugin.solr.wrappers;

import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HttpContext;
import org.apache.skywalking.apm.agent.core.context.CarrierItem;
import org.apache.skywalking.apm.agent.core.context.ContextCarrier;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;

import java.io.IOException;

public class CloaseableHttpClientWrapper extends CloseableHttpClient {
    private static final ILog LOG = LogManager.getLogger(CloaseableHttpClientWrapper.class);
    private final CloseableHttpClient client;

    public CloaseableHttpClientWrapper(CloseableHttpClient client) {
        this.client = client;
    }

    @Override
    public HttpParams getParams() {
        return client.getParams();
    }

    @Override
    public ClientConnectionManager getConnectionManager() {
        return client.getConnectionManager();
    }

    @Override
    public void close() throws IOException {
        client.close();
    }

    @Override
    protected CloseableHttpResponse doExecute(HttpHost target, HttpRequest request, HttpContext context)
            throws IOException, ClientProtocolException {
        try {
            ContextCarrier carrier = new ContextCarrier();
            ContextManager.inject(carrier);
            CarrierItem items = carrier.items();
            while (items.hasNext()) {
                items = items.next();
                request.addHeader(items.getHeadKey(), items.getHeadValue());
            }
        } catch (Throwable throwable) {
            LOG.error("", throwable);
        }
        return client.execute(target, request, context);
    }

}
