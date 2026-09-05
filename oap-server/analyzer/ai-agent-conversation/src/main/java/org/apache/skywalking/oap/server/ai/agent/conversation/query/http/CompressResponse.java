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

package org.apache.skywalking.oap.server.ai.agent.conversation.query.http;

import com.linecorp.armeria.common.HttpRequest;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.server.HttpService;
import com.linecorp.armeria.server.ServiceRequestContext;
import com.linecorp.armeria.server.DecoratingHttpServiceFunction;
import com.linecorp.armeria.server.encoding.EncodingService;
import java.util.function.Function;

/**
 * Compresses a JSON or YAML response when the client's <code>Accept-Encoding</code> allows it, chunk by chunk,
 * so a streamed document stays streamed. A document is repetitive text and shrinks several times over.
 */
public final class CompressResponse implements DecoratingHttpServiceFunction {
    private static final Function<? super HttpService, EncodingService> ENCODING = EncodingService.builder()
        .encodableContentTypes(ConversationViewHandler.JSON, ConversationViewHandler.YAML)
        .newDecorator();

    @Override
    public HttpResponse serve(final HttpService delegate, final ServiceRequestContext ctx, final HttpRequest req)
        throws Exception {
        return delegate.decorate(ENCODING).serve(ctx, req);
    }
}
