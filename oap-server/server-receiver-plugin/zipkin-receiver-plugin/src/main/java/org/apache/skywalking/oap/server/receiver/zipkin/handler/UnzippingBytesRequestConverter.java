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

import com.linecorp.armeria.common.AggregatedHttpRequest;
import com.linecorp.armeria.common.HttpData;
import com.linecorp.armeria.common.HttpHeaderNames;
import com.linecorp.armeria.common.encoding.StreamDecoderFactory;
import com.linecorp.armeria.server.ServiceRequestContext;

final class UnzippingBytesRequestConverter {

    static HttpData convertRequest(ServiceRequestContext ctx, AggregatedHttpRequest request) {
        String encoding = request.headers().get(HttpHeaderNames.CONTENT_ENCODING);
        HttpData content = request.content();
        if (!content.isEmpty() && encoding != null && encoding.contains("gzip")) {
            content = StreamDecoderFactory.gzip().newDecoder(ctx.alloc()).decode(content);
            if (content.isEmpty()) {
                content.close();
                throw new IllegalArgumentException("Cannot unzip request content bytes");
            }
        }
        return content;
    }
}
