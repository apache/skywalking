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

import org.apache.skywalking.apm.collector.receiver.zipkin.provider.RegisterServices;
import org.apache.skywalking.apm.collector.receiver.zipkin.provider.ZipkinReceiverConfig;
import org.apache.skywalking.apm.collector.receiver.zipkin.provider.cache.CacheFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zipkin2.Span;
import zipkin2.codec.SpanBytesDecoder;

import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.List;

public class SpanProcessor {
    private final Logger logger = LoggerFactory.getLogger(SpanProcessor.class);

    void convert(ZipkinReceiverConfig config, SpanBytesDecoder decoder, HttpServletRequest request, RegisterServices registerServices) throws IOException {
        int len = request.getContentLength();
        ServletInputStream iii = request.getInputStream();
        byte[] buffer = new byte[len];

        int readCntTotal = 0;
        int readCntOnce;
        while (readCntTotal < len) {
            readCntOnce = iii.read(buffer, readCntTotal, len - readCntTotal);
            if (readCntOnce <= 0) {
                logger.error("Receive spans data failed.");
                throw new IOException();
            }
            readCntTotal += readCntOnce;
        }

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
    }
}
