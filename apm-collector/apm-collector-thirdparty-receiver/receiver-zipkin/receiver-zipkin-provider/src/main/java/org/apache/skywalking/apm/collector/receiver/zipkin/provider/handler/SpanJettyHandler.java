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

import java.util.List;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.skywalking.apm.collector.receiver.zipkin.provider.RegisterServices;
import org.apache.skywalking.apm.collector.receiver.zipkin.provider.ZipkinReceiverConfig;
import org.apache.skywalking.apm.collector.receiver.zipkin.provider.cache.CacheFactory;
import org.apache.skywalking.apm.collector.server.jetty.JettyHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zipkin2.Span;
import zipkin2.codec.SpanBytesDecoder;

/**
 * @author wusheng
 */
public class SpanJettyHandler extends JettyHandler {
    private static final Logger logger = LoggerFactory.getLogger(SpanJettyHandler.class);

    private ZipkinReceiverConfig config;
    private RegisterServices registerServices;

    public SpanJettyHandler(ZipkinReceiverConfig config,
        RegisterServices registerServices) {
        this.config = config;
        this.registerServices = registerServices;
    }

    @Override public String pathSpec() {
        return "/api/v2/spans";
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        response.setContentType("application/json");
        response.setCharacterEncoding("utf-8");

        try {
            String type = request.getHeader("Content-Type");

            SpanBytesDecoder decoder = type != null && type.contains("/x-protobuf")
                ? SpanBytesDecoder.PROTO3
                : SpanBytesDecoder.JSON_V2;

            int len = request.getContentLength();
            ServletInputStream iii = request.getInputStream();
            byte[] buffer = new byte[len];
            iii.read(buffer, 0, len);

            List<Span> spanList = decoder.decodeList(buffer);

            spanList.forEach(span -> {
                // In Zipkin, the local service name represents the application owner.
                String applicationCode = span.localServiceName();
                if (applicationCode != null) {
                    int applicationId = registerServices.getApplicationIDService().getOrCreateForApplicationCode(applicationCode);
                    if (applicationId != 0) {
                        registerServices.getOrCreateApplicationInstanceId(applicationId, applicationCode);
                    }
                }
                CacheFactory.INSTANCE.get(config).addSpan(span);
            });

            response.setStatus(202);
        } catch (Exception e) {
            response.setStatus(500);

            logger.error(e.getMessage(), e);
        }
    }
}
