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
 */

package org.apache.skywalking.apm.plugin.httpasyncclient.v4.wrapper;

import org.apache.http.HttpException;
import org.apache.http.HttpResponse;
import org.apache.http.nio.ContentDecoder;
import org.apache.http.nio.IOControl;
import org.apache.http.nio.protocol.HttpAsyncResponseConsumer;
import org.apache.http.protocol.HttpContext;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;

import java.io.IOException;

import static org.apache.skywalking.apm.plugin.httpasyncclient.v4.SessionRequestCompleteInterceptor.CONTEXT_LOCAL;

/**
 * a wrapper for {@link HttpAsyncResponseConsumer} so we can be notified when the current response(every response will
 * callback the wrapper) received maybe completed or canceled,or failed.
 */
public class HttpAsyncResponseConsumerWrapper<T> implements HttpAsyncResponseConsumer<T> {

    private HttpAsyncResponseConsumer<T> consumer;

    public HttpAsyncResponseConsumerWrapper(HttpAsyncResponseConsumer<T> consumer) {
        this.consumer = consumer;
    }

    @Override
    public void responseReceived(HttpResponse response) throws IOException, HttpException {
        if (ContextManager.isActive()) {
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode >= 400) {
                AbstractSpan span = ContextManager.activeSpan().errorOccurred();
                Tags.STATUS_CODE.set(span, String.valueOf(statusCode));
            }
            ContextManager.stopSpan();
        }
        consumer.responseReceived(response);
    }

    @Override
    public void consumeContent(ContentDecoder decoder, IOControl ioctrl) throws IOException {
        consumer.consumeContent(decoder, ioctrl);
    }

    @Override
    public void responseCompleted(HttpContext context) {
        consumer.responseCompleted(context);
    }

    @Override
    public void failed(Exception ex) {
        CONTEXT_LOCAL.remove();
        if (ContextManager.isActive()) {
            ContextManager.activeSpan().log(ex);
            ContextManager.stopSpan();
        }
        consumer.failed(ex);

    }

    @Override
    public Exception getException() {
        return consumer.getException();
    }

    @Override
    public T getResult() {
        return consumer.getResult();
    }

    @Override
    public boolean isDone() {
        return consumer.isDone();
    }

    @Override
    public void close() throws IOException {
        consumer.close();
    }

    @Override
    public boolean cancel() {
        CONTEXT_LOCAL.remove();
        if (ContextManager.isActive()) {
            ContextManager.activeSpan().errorOccurred();
            ContextManager.stopSpan();
        }
        return consumer.cancel();
    }
}
