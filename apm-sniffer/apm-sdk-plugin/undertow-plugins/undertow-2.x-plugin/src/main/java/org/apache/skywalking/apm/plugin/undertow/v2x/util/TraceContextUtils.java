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
package org.apache.skywalking.apm.plugin.undertow.v2x.util;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.HeaderMap;
import org.apache.skywalking.apm.agent.core.context.CarrierItem;
import org.apache.skywalking.apm.agent.core.context.ContextCarrier;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author AI
 * 2019-07-26
 */
public class TraceContextUtils {

    private static final AtomicBoolean IS_IN_ROUTING_HANDLER_TRACE = new AtomicBoolean(false);

    private TraceContextUtils() {
    }

    public static ContextCarrier buildContextCarrier(HeaderMap headers) {
        ContextCarrier carrier = new ContextCarrier();
        CarrierItem items = carrier.items();
        while (items.hasNext()) {
            items = items.next();
            items.setHeadValue(headers.getFirst(items.getHeadKey()));
        }
        return carrier;
    }

    public static AbstractSpan buildUndertowEntrySpan(HttpServerExchange exchange, String operationName) {
        ContextCarrier carrier = TraceContextUtils.buildContextCarrier(exchange.getRequestHeaders());
        final AbstractSpan span = ContextManager.createEntrySpan(operationName, carrier);
        Tags.URL.set(span, exchange.getRequestURL());
        Tags.HTTP.METHOD.set(span, exchange.getRequestMethod().toString());
        span.setComponent(ComponentsDefine.UNDERTOW);
        SpanLayer.asHttp(span);
        return span;
    }

    public static void enabledRoutingHandlerTracing() {
        IS_IN_ROUTING_HANDLER_TRACE.set(true);
    }

    public static boolean isInRoutingHandlerTracing() {
        return IS_IN_ROUTING_HANDLER_TRACE.get();
    }

    public static boolean isNotInRoutingHandlerTracing() {
        return !isInRoutingHandlerTracing();
    }

}
