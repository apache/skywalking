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

package org.apache.skywalking.oap.server.receiver.zipkin.handler;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.source.SourceReceiver;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.server.jetty.JettyHandler;
import org.apache.skywalking.oap.server.receiver.zipkin.ZipkinReceiverConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zipkin2.codec.SpanBytesDecoder;

public class SpanV2JettyHandler extends JettyHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(SpanV2JettyHandler.class);

    private ZipkinReceiverConfig config;
    private SourceReceiver sourceReceiver;

    public SpanV2JettyHandler(ZipkinReceiverConfig config, ModuleManager manager) {
        sourceReceiver = manager.find(CoreModule.NAME).provider().getService(SourceReceiver.class);
        this.config = config;
    }

    @Override
    public String pathSpec() {
        return "/api/v2/spans";
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        response.setContentType("application/json");
        response.setCharacterEncoding("utf-8");

        try {
            String type = request.getHeader("Content-Type");

            int encode = type != null && type.contains("/x-protobuf") ? SpanEncode.PROTO3 : SpanEncode.JSON_V2;

            SpanBytesDecoder decoder = SpanEncode.isProto3(encode) ? SpanBytesDecoder.PROTO3 : SpanBytesDecoder.JSON_V2;

            SpanProcessor processor = new SpanProcessor(sourceReceiver);
            processor.convert(config, decoder, request);

            response.setStatus(202);
        } catch (Exception e) {
            response.setStatus(500);

            LOGGER.error(e.getMessage(), e);
        }
    }
}
