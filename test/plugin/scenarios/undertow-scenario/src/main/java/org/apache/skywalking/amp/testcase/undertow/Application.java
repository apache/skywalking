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

package org.apache.skywalking.amp.testcase.undertow;

import io.undertow.Undertow;
import io.undertow.server.ExchangeCompletionListener;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;

import java.lang.reflect.Field;

public class Application {

    public static void main(String[] args) {
        Undertow server = Undertow.builder()
            .addHttpListener(8080, "127.0.0.1")
            .setHandler(new HttpHandler() {
                @Override
                public void handleRequest(final HttpServerExchange exchange) throws Exception {
//                    invokeExchangeCompleteListeners(exchange);
                    exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/plain");
                    exchange.getResponseSender().send("Success");
                }
            }).build();
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
        server.start();
    }

    private static void invokeExchangeCompleteListeners(HttpServerExchange exchange) throws NoSuchFieldException, IllegalAccessException {
        Field listenersField = exchange.getClass().getDeclaredField("exchangeCompleteListeners");
        listenersField.setAccessible(true);
        Field listenersCountField = exchange.getClass().getDeclaredField("exchangeCompletionListenersCount");
        listenersCountField.setAccessible(true);

        ExchangeCompletionListener[] exchangeCompleteListeners = (ExchangeCompletionListener[]) listenersField.get(exchange);
        listenersField.set(exchange, null);
        int exchangeCompletionListenersCount = listenersCountField.getInt(exchange);
        listenersCountField.setInt(exchange, 0);

        if (exchangeCompletionListenersCount > 0) {
            int i = exchangeCompletionListenersCount - 1;
            ExchangeCompletionListener next = exchangeCompleteListeners[i];
            next.exchangeEvent(exchange, new ExchangeCompleteNextListener(exchangeCompleteListeners, exchange, i));
        }
    }

    private static class ExchangeCompleteNextListener implements ExchangeCompletionListener.NextListener {
        private final ExchangeCompletionListener[] list;
        private final HttpServerExchange exchange;
        private int i;

        private ExchangeCompleteNextListener(final ExchangeCompletionListener[] list, final HttpServerExchange exchange, int i) {
            this.list = list;
            this.exchange = exchange;
            this.i = i;
        }

        @Override
        public void proceed() {
            if (--i >= 0) {
                final ExchangeCompletionListener next = list[i];
                next.exchangeEvent(exchange, this);
            } else if (i == -1) {
                // ignore
            }
        }
    }
}
