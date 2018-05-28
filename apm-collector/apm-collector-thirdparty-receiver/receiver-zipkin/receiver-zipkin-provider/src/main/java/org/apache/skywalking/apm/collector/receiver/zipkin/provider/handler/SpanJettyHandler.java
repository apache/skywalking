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

package org.apache.skywalking.apm.collector.receiver.zipkin.provider.handler;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;
import java.io.BufferedReader;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.skywalking.apm.collector.receiver.zipkin.provider.ZipkinReceiverConfig;
import org.apache.skywalking.apm.collector.receiver.zipkin.provider.cache.CacheFactory;
import org.apache.skywalking.apm.collector.receiver.zipkin.provider.data.ZipkinSpan;
import org.apache.skywalking.apm.collector.server.jetty.ArgumentsParseException;
import org.apache.skywalking.apm.collector.server.jetty.JettyHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author wusheng
 */
public class SpanJettyHandler extends JettyHandler {
    private static final Logger logger = LoggerFactory.getLogger(SpanJettyHandler.class);

    private Gson gson;
    private Type spanListType;
    private ZipkinReceiverConfig config;

    public SpanJettyHandler(ZipkinReceiverConfig config) {
        this.config = config;
        gson = new Gson();
        spanListType = new TypeToken<List<ZipkinSpan>>() {
        }.getType();
    }

    @Override public String pathSpec() {
        return "/api/v2/spans";
    }

    @Override protected JsonElement doGet(HttpServletRequest req) throws ArgumentsParseException {
        return null;
    }

    protected void doPost(HttpServletRequest req, HttpServletResponse response) {
        response.setContentType("application/json");
        response.setCharacterEncoding("utf-8");

        try {
            BufferedReader br = req.getReader();

            List<ZipkinSpan> spans = gson.fromJson(br, spanListType);
            spans.forEach(span ->
                CacheFactory.INSTANCE.get(config).addSpan(span)
            );

            response.setStatus(202);
        } catch (Exception e) {
            response.setStatus(500);

            logger.error(e.getMessage(), e);
        }
    }

    /**
     * Zipkin doesn't request a Json format response.
     * Implement {@link #doPost(HttpServletRequest, HttpServletResponse)} by following zipkin protocol.
     * Leave this method no implementation.
     *
     * @param req
     * @return
     * @throws ArgumentsParseException
     * @throws IOException
     */
    @Override
    protected JsonElement doPost(HttpServletRequest req) throws ArgumentsParseException, IOException {
        throw new UnsupportedOperationException();
    }
}
