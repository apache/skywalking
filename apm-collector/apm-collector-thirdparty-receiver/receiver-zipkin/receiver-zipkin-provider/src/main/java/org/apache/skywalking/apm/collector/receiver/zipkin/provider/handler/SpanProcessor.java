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
import zipkin2.Span;
import zipkin2.codec.SpanBytesDecoder;

import javax.servlet.http.HttpServletRequest;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.zip.GZIPInputStream;

public class SpanProcessor {
    void convert(ZipkinReceiverConfig config, SpanBytesDecoder decoder, HttpServletRequest request, RegisterServices registerServices) throws IOException {
        InputStream inputStream = getInputStream(request);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[2048];
        int readCntOnce;

        while ((readCntOnce = inputStream.read(buffer)) >= 0) {
            out.write(buffer, 0, readCntOnce);
        }

        List<Span> spanList = decoder.decodeList(out.toByteArray());

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

    private InputStream getInputStream(HttpServletRequest request) throws IOException {
        InputStream requestInStream;

        String headEncoding = request.getHeader("accept-encoding");
        if (headEncoding != null && (headEncoding.indexOf("gzip") != -1)) {
            requestInStream = new GZIPInputStream(request.getInputStream());
        } else {
            requestInStream = request.getInputStream();
        }

        return requestInStream;
    }
}
